package com.game.imposter.service;

import com.game.imposter.dto.*;
import com.game.imposter.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final WordService wordService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(20);

    private static final String[] COLORS = {
        "#FF4444", "#44AAFF", "#44DD66", "#FFAA00",
        "#BB44FF", "#FF44BB", "#00DDDD", "#FFFF44"
    };

    // ── Room management ────────────────────────────────────────────────────────

    public RoomResponse createRoom(String playerName) {
        String roomCode = generateRoomCode();
        String playerId = UUID.randomUUID().toString();
        Player host = buildPlayer(playerId, playerName, COLORS[0]);

        GameRoom room = new GameRoom();
        room.setRoomCode(roomCode);
        room.setHostId(playerId);
        room.getPlayers().put(playerId, host);
        rooms.put(roomCode, room);

        return RoomResponse.builder().roomCode(roomCode).playerId(playerId).build();
    }

    public RoomResponse joinRoom(String roomCode, String playerName) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.LOBBY) throw new IllegalStateException("Game already started");
        if (room.getPlayers().size() >= 8) throw new IllegalStateException("Room is full (max 8)");

        String playerId = UUID.randomUUID().toString();
        String color = COLORS[room.getPlayers().size() % COLORS.length];
        Player player = buildPlayer(playerId, playerName, color);
        room.getPlayers().put(playerId, player);

        broadcast(roomCode, "PLAYER_JOINED", Map.of("playerName", player.getName()));
        return RoomResponse.builder().roomCode(roomCode).playerId(playerId).build();
    }

    // ── Game start ─────────────────────────────────────────────────────────────

    public void startGame(String roomCode, String playerId) {
        GameRoom room = requireRoom(roomCode);
        if (!room.getHostId().equals(playerId)) throw new IllegalStateException("Only host can start");
        if (room.getPlayers().size() < 3) throw new IllegalStateException("Need at least 3 players");
        if (room.getPhase() != GamePhase.LOBBY) throw new IllegalStateException("Game already started");

        assignWords(room);
        room.setRound(1);
        transitionTo(room, GamePhase.WORD_REVEAL);
    }

    // ── State query ────────────────────────────────────────────────────────────

    public GameStateDto getState(String roomCode, String playerId) {
        GameRoom room = requireRoom(roomCode);
        Player me = room.getPlayers().get(playerId);

        // Reveal words only in RESULT phase
        String revealedSecretWord = null;
        String revealedImposterWord = null;
        String imposterName = null;
        if (room.getPhase() == GamePhase.RESULT) {
            revealedSecretWord = room.getSecretWord();
            revealedImposterWord = room.getImposterWord();
            imposterName = room.getPlayers().values().stream()
                    .filter(p -> p.getRole() == PlayerRole.IMPOSTER)
                    .map(Player::getName).findFirst().orElse("Unknown");
        }

        Map<String, Integer> voteCounts = new HashMap<>();
        room.getVotes().values().forEach(tid -> voteCounts.merge(tid, 1, Integer::sum));

        return GameStateDto.builder()
                .roomCode(roomCode)
                .phase(room.getPhase())
                .round(room.getRound())
                .players(room.getPlayers().values().stream()
                        .sorted(Comparator.comparing(Player::getName))
                        .map(p -> toDto(p, room))
                        .collect(Collectors.toList()))
                .myRole(me != null ? me.getRole() : null)
                .myWord(me != null ? me.getAssignedWord() : null)
                .myClueGiven(me != null && me.isClueGivenThisRound())
                .allClues(new ArrayList<>(room.getAllClues()))
                .voteCounts(voteCounts)
                .winner(room.getWinner())
                .imposterCaught(room.isImposterCaught())
                .secretWord(revealedSecretWord)
                .imposterWord(revealedImposterWord)
                .imposterGuessCorrect(room.isImposterGuessCorrect())
                .imposterName(imposterName)
                .phaseEndsAt(room.getPhaseEndsAt())
                .hostId(room.getHostId())
                .build();
    }

    // ── Player actions ─────────────────────────────────────────────────────────

    public void submitClue(String roomCode, String playerId, String clue) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.CLUE_ROUND) return;

        Player player = room.getPlayers().get(playerId);
        if (player == null || player.isClueGivenThisRound()) return;

        // Accept first word only, trim whitespace, limit length
        String cleanClue = clue == null ? "" : clue.trim().split("\\s+")[0];
        if (cleanClue.isEmpty() || cleanClue.length() > 30) return;

        synchronized (room) {
            if (player.isClueGivenThisRound()) return; // double-check inside lock
            player.setClueGivenThisRound(true);
            player.getClues().add(cleanClue);

            ClueEntry entry = ClueEntry.builder()
                    .playerId(playerId)
                    .playerName(player.getName())
                    .color(player.getColor())
                    .word(cleanClue)
                    .round(room.getRound())
                    .build();
            room.getAllClues().add(entry);

            broadcast(roomCode, "CLUE_SUBMITTED", Map.of(
                    "playerId", playerId,
                    "playerName", player.getName(),
                    "color", player.getColor(),
                    "word", cleanClue,
                    "round", room.getRound()));

            // Advance early if everyone has submitted
            long total = room.getPlayers().size();
            long submitted = room.getPlayers().values().stream()
                    .filter(Player::isClueGivenThisRound).count();
            if (submitted >= total) {
                advanceFromClueRound(room);
            }
        }
    }

    public void castVote(String roomCode, String voterId, String targetId) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.VOTING) return;

        Player voter = room.getPlayers().get(voterId);
        if (voter == null || voter.isHasVoted()) return;

        synchronized (room) {
            if (voter.isHasVoted()) return;
            voter.setHasVoted(true);
            room.getVotes().put(voterId, targetId != null ? targetId : "SKIP");
            broadcast(roomCode, "VOTE_CAST", Map.of("voterName", voter.getName()));

            long playerCount = room.getPlayers().size();
            if (room.getVotes().size() >= playerCount) {
                processVotes(room);
            }
        }
    }

    public void submitGuess(String roomCode, String playerId, String guess) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.IMPOSTER_GUESS) return;

        Player player = room.getPlayers().get(playerId);
        if (player == null || player.getRole() != PlayerRole.IMPOSTER) return;

        String cleanGuess = guess == null ? "" : guess.trim();
        if (cleanGuess.isEmpty()) return;

        room.setImposterGuessWord(cleanGuess);
        room.setImposterGuessCorrect(cleanGuess.equalsIgnoreCase(room.getSecretWord()));

        cancelTimer(room);
        endGame(room);
    }

    // ── Phase transitions ──────────────────────────────────────────────────────

    private void transitionTo(GameRoom room, GamePhase next) {
        cancelTimer(room);
        room.setPhase(next);
        String code = room.getRoomCode();

        switch (next) {
            case WORD_REVEAL -> {
                room.setPhaseEndsAt(System.currentTimeMillis() + 6_000);
                broadcast(code, "PHASE_CHANGE", Map.of("phase", "WORD_REVEAL", "duration", 6));
                room.setPhaseTimer(scheduler.schedule(
                        () -> startClueRound(room), 6, TimeUnit.SECONDS));
            }
            case VOTING -> {
                room.setVotes(new ConcurrentHashMap<>());
                room.getPlayers().values().forEach(p -> p.setHasVoted(false));
                room.setPhaseEndsAt(System.currentTimeMillis() + 30_000);
                broadcast(code, "PHASE_CHANGE", Map.of("phase", "VOTING", "duration", 30));
                room.setPhaseTimer(scheduler.schedule(
                        () -> processVotes(room), 30, TimeUnit.SECONDS));
            }
            case IMPOSTER_GUESS -> {
                room.setPhaseEndsAt(System.currentTimeMillis() + 15_000);
                broadcast(code, "PHASE_CHANGE", Map.of("phase", "IMPOSTER_GUESS", "duration", 15));
                room.setPhaseTimer(scheduler.schedule(
                        () -> endGame(room), 15, TimeUnit.SECONDS));
            }
            case RESULT -> endGame(room);
            default -> { }
        }
    }

    /** Starts (or restarts) the CLUE_ROUND phase for the current round number. */
    private void startClueRound(GameRoom room) {
        cancelTimer(room);
        room.setPhase(GamePhase.CLUE_ROUND);
        room.getPlayers().values().forEach(p -> p.setClueGivenThisRound(false));
        room.setPhaseEndsAt(System.currentTimeMillis() + 60_000);
        broadcast(room.getRoomCode(), "PHASE_CHANGE",
                Map.of("phase", "CLUE_ROUND", "round", room.getRound(), "duration", 60));
        room.setPhaseTimer(scheduler.schedule(
                () -> advanceFromClueRound(room), 60, TimeUnit.SECONDS));
    }

    /** Called when a clue round ends (timer or early exit). */
    private void advanceFromClueRound(GameRoom room) {
        cancelTimer(room);
        if (room.getRound() < 2) {
            room.setRound(room.getRound() + 1);
            startClueRound(room);
        } else {
            transitionTo(room, GamePhase.VOTING);
        }
    }

    private void processVotes(GameRoom room) {
        cancelTimer(room);
        String imposterId = getImposterId(room);

        // Tally votes (skip "SKIP")
        Map<String, Long> counts = room.getVotes().values().stream()
                .filter(v -> !"SKIP".equals(v))
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        String topVoted = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        boolean caught = topVoted != null && topVoted.equals(imposterId);
        room.setImposterCaught(caught);

        broadcast(room.getRoomCode(), "VOTE_RESULT", Map.of(
                "imposterCaught", caught,
                "topVotedId", topVoted != null ? topVoted : ""));

        if (caught) {
            transitionTo(room, GamePhase.IMPOSTER_GUESS);
        } else {
            endGame(room);
        }
    }

    private void endGame(GameRoom room) {
        cancelTimer(room);
        calculateScores(room);
        room.setPhase(GamePhase.RESULT);
        room.setPhaseEndsAt(0);

        String imposterId = getImposterId(room);
        String imposterName = imposterId != null
                ? room.getPlayers().get(imposterId).getName() : "Unknown";

        broadcast(room.getRoomCode(), "GAME_ENDED", Map.of(
                "winner", room.getWinner() != null ? room.getWinner() : "UNKNOWN",
                "imposterName", imposterName,
                "secretWord", room.getSecretWord() != null ? room.getSecretWord() : "",
                "imposterWord", room.getImposterWord() != null ? room.getImposterWord() : "",
                "imposterGuessCorrect", room.isImposterGuessCorrect()));
    }

    private void calculateScores(GameRoom room) {
        String imposterId = getImposterId(room);
        if (imposterId == null) return;

        if (room.isImposterCaught()) {
            room.setWinner("CREWMATES");
            // +2 for each player who voted for the imposter
            room.getVotes().forEach((voterId, targetId) -> {
                if (imposterId.equals(targetId)) {
                    Player voter = room.getPlayers().get(voterId);
                    if (voter != null) voter.setScore(voter.getScore() + 2);
                }
            });
            // +2 bonus if the imposter guessed the secret word correctly
            if (room.isImposterGuessCorrect()) {
                Player imp = room.getPlayers().get(imposterId);
                if (imp != null) imp.setScore(imp.getScore() + 2);
            }
        } else {
            room.setWinner("IMPOSTER");
            // +3 for the imposter escaping
            Player imp = room.getPlayers().get(imposterId);
            if (imp != null) imp.setScore(imp.getScore() + 3);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void assignWords(GameRoom room) {
        String[] pair = wordService.getRandomPair();
        room.setSecretWord(pair[0]);
        room.setImposterWord(pair[1]);

        // Reset clue history and scores
        room.setAllClues(new ArrayList<>());
        room.setWinner(null);
        room.setImposterCaught(false);
        room.setImposterGuessWord(null);
        room.setImposterGuessCorrect(false);

        List<String> ids = new ArrayList<>(room.getPlayers().keySet());
        Collections.shuffle(ids);

        // First player in shuffled list becomes the imposter
        String imposterId = ids.get(0);

        for (String id : ids) {
            Player p = room.getPlayers().get(id);
            p.setClues(new ArrayList<>());
            p.setScore(0);
            p.setClueGivenThisRound(false);
            p.setHasVoted(false);
            if (id.equals(imposterId)) {
                p.setRole(PlayerRole.IMPOSTER);
                p.setAssignedWord(pair[1]);
            } else {
                p.setRole(PlayerRole.CREWMATE);
                p.setAssignedWord(pair[0]);
            }
        }
    }

    private String getImposterId(GameRoom room) {
        return room.getPlayers().values().stream()
                .filter(p -> p.getRole() == PlayerRole.IMPOSTER)
                .map(Player::getId)
                .findFirst().orElse(null);
    }

    private void cancelTimer(GameRoom room) {
        if (room.getPhaseTimer() != null) room.getPhaseTimer().cancel(false);
    }

    private void broadcast(String roomCode, String type, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + roomCode,
                GameEventMessage.builder().type(type).payload(payload).build());
    }

    private GameRoom requireRoom(String roomCode) {
        GameRoom room = rooms.get(roomCode);
        if (room == null) throw new NoSuchElementException("Room not found: " + roomCode);
        return room;
    }

    private Player buildPlayer(String id, String name, String color) {
        return Player.builder().id(id).name(name).color(color).build();
    }

    private PlayerDto toDto(Player p, GameRoom room) {
        return PlayerDto.builder()
                .id(p.getId())
                .name(p.getName())
                .color(p.getColor())
                .host(p.getId().equals(room.getHostId()))
                .hasVoted(p.isHasVoted())
                .clueGivenThisRound(p.isClueGivenThisRound())
                .score(p.getScore())
                .clues(new ArrayList<>(p.getClues()))
                .build();
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
