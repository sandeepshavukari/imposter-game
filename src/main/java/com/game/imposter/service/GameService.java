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
    private final TaskService taskService;
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

        broadcast(roomCode, "PLAYER_JOINED", Map.of("player", toDto(player, room)));
        return RoomResponse.builder().roomCode(roomCode).playerId(playerId).build();
    }

    // ── Game start ─────────────────────────────────────────────────────────────

    public void startGame(String roomCode, String playerId) {
        GameRoom room = requireRoom(roomCode);
        if (!room.getHostId().equals(playerId)) throw new IllegalStateException("Only host can start");
        if (room.getPlayers().size() < 3) throw new IllegalStateException("Need at least 3 players");
        if (room.getPhase() != GamePhase.LOBBY) throw new IllegalStateException("Game already started");

        assignRolesAndTasks(room);
        room.setRound(1);
        room.setPhase(GamePhase.ROLE_REVEAL);
        room.setPhaseEndsAt(System.currentTimeMillis() + 4_000);

        broadcast(roomCode, "GAME_STARTED", Map.of("phase", "ROLE_REVEAL"));
        schedule(room, GamePhase.TASK, 4);
    }

    // ── State query ────────────────────────────────────────────────────────────

    public GameStateDto getState(String roomCode, String playerId) {
        GameRoom room = requireRoom(roomCode);
        Player me = room.getPlayers().get(playerId);

        Task currentTask = null;
        if (room.getPhase() == GamePhase.TASK && me != null && me.isAlive()
                && me.getRole() == PlayerRole.CREWMATE) {
            List<Task> tasks = me.getTasks();
            int idx = me.getCurrentTaskIndex();
            if (tasks != null && idx < tasks.size()) currentTask = tasks.get(idx);
        }

        Map<String, Integer> voteCounts = new HashMap<>();
        room.getVotes().values().forEach(tid -> voteCounts.merge(tid, 1, Integer::sum));

        String lastEjectedName = null;
        if (room.getLastEjectedId() != null) {
            Player ej = room.getPlayers().get(room.getLastEjectedId());
            if (ej != null) lastEjectedName = ej.getName();
        } else if (room.getPhase() == GamePhase.VOTE_RESULT) {
            lastEjectedName = "Nobody";
        }

        String imposterName = null;
        if (room.getPhase() == GamePhase.ENDED) {
            imposterName = room.getPlayers().values().stream()
                    .filter(p -> p.getRole() == PlayerRole.IMPOSTER)
                    .map(Player::getName).findFirst().orElse("Unknown");
        }

        return GameStateDto.builder()
                .roomCode(roomCode)
                .phase(room.getPhase())
                .round(room.getRound())
                .players(room.getPlayers().values().stream().map(p -> toDto(p, room)).collect(Collectors.toList()))
                .myRole(me != null ? me.getRole() : null)
                .currentTask(currentTask)
                .chat(new ArrayList<>(room.getChat()))
                .voteCounts(voteCounts)
                .winner(room.getWinner())
                .lastEjectedName(lastEjectedName)
                .lastEjectedWasImposter(room.isLastEjectedWasImposter())
                .imposterName(imposterName)
                .phaseEndsAt(room.getPhaseEndsAt())
                .emergencyMeetingUsed(room.isEmergencyMeetingUsed())
                .imposterKillUsed(room.isImposterKillUsed())
                .hostId(room.getHostId())
                .build();
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    public void completeTask(String roomCode, String playerId, String answer) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.TASK) return;
        Player player = room.getPlayers().get(playerId);
        if (player == null || !player.isAlive() || player.getRole() != PlayerRole.CREWMATE) return;

        List<Task> tasks = player.getTasks();
        int idx = player.getCurrentTaskIndex();
        if (tasks == null || idx >= tasks.size()) return;
        Task task = tasks.get(idx);
        if (task.isCompleted()) return;

        if (task.getCorrectAnswer().equals(answer)) {
            task.setCompleted(true);
            player.setTasksCompleted(player.getTasksCompleted() + 1);
            player.setCurrentTaskIndex(idx + 1);
            broadcast(roomCode, "TASK_COMPLETED", Map.of("playerName", player.getName()));
            checkCrewWin(room);
        }
    }

    public void eliminate(String roomCode, String imposterId, String targetId) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.TASK || room.isImposterKillUsed()) return;
        Player imposter = room.getPlayers().get(imposterId);
        if (imposter == null || imposter.getRole() != PlayerRole.IMPOSTER || !imposter.isAlive()) return;
        Player target = room.getPlayers().get(targetId);
        if (target == null || !target.isAlive() || target.getRole() == PlayerRole.IMPOSTER) return;

        target.setAlive(false);
        room.setImposterKillUsed(true);
        broadcast(roomCode, "PLAYER_ELIMINATED", Map.of(
                "playerId", targetId, "playerName", target.getName()));
        checkImposterWin(room);
    }

    public void callMeeting(String roomCode, String callerId) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.TASK || room.isEmergencyMeetingUsed()) return;
        Player caller = room.getPlayers().get(callerId);
        if (caller == null || !caller.isAlive()) return;

        room.setEmergencyMeetingUsed(true);
        broadcast(roomCode, "EMERGENCY_MEETING", Map.of("callerName", caller.getName()));
        transitionTo(room, GamePhase.DISCUSSION);
    }

    public void sendChat(String roomCode, String senderId, String message) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.DISCUSSION) return;
        Player sender = room.getPlayers().get(senderId);
        if (sender == null || !sender.isAlive()) return;

        ChatMessage msg = ChatMessage.builder()
                .senderId(senderId).senderName(sender.getName())
                .message(message).timestamp(System.currentTimeMillis()).build();
        room.getChat().add(msg);
        broadcast(roomCode, "CHAT_MESSAGE", msg);
    }

    public void castVote(String roomCode, String voterId, String targetId) {
        GameRoom room = requireRoom(roomCode);
        if (room.getPhase() != GamePhase.VOTING) return;
        Player voter = room.getPlayers().get(voterId);
        if (voter == null || !voter.isAlive() || voter.isHasVoted()) return;

        voter.setHasVoted(true);
        room.getVotes().put(voterId, targetId != null ? targetId : "SKIP");
        broadcast(roomCode, "VOTE_CAST", Map.of("voterName", voter.getName()));

        long aliveCount = room.getPlayers().values().stream().filter(Player::isAlive).count();
        if (room.getVotes().size() >= aliveCount) processVotes(room);
    }

    // ── Phase transitions ──────────────────────────────────────────────────────

    private void transitionTo(GameRoom room, GamePhase next) {
        cancelTimer(room);
        room.setPhase(next);
        switch (next) {
            case TASK -> {
                room.setImposterKillUsed(false);
                room.getPlayers().values().forEach(p -> p.setHasVoted(false));
                room.setPhaseEndsAt(System.currentTimeMillis() + 45_000);
                broadcast(room.getRoomCode(), "PHASE_CHANGE", Map.of("phase", "TASK", "duration", 45));
                schedule(room, GamePhase.DISCUSSION, 45);
            }
            case DISCUSSION -> {
                room.setPhaseEndsAt(System.currentTimeMillis() + 30_000);
                broadcast(room.getRoomCode(), "PHASE_CHANGE", Map.of("phase", "DISCUSSION", "duration", 30));
                schedule(room, GamePhase.VOTING, 30);
            }
            case VOTING -> {
                room.setVotes(new ConcurrentHashMap<>());
                room.getPlayers().values().forEach(p -> p.setHasVoted(false));
                room.setPhaseEndsAt(System.currentTimeMillis() + 20_000);
                broadcast(room.getRoomCode(), "PHASE_CHANGE", Map.of("phase", "VOTING", "duration", 20));
                schedule(room, GamePhase.VOTE_RESULT, 20);
            }
            case VOTE_RESULT -> processVotes(room);
            default -> { }
        }
    }

    private void processVotes(GameRoom room) {
        cancelTimer(room);
        room.setPhase(GamePhase.VOTE_RESULT);

        Map<String, Long> counts = room.getVotes().values().stream()
                .filter(v -> !"SKIP".equals(v))
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        String ejectedId = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        if (ejectedId != null) {
            long max = counts.get(ejectedId);
            if (counts.values().stream().filter(v -> v == max).count() > 1) ejectedId = null;
        }

        boolean wasImposter = false;
        String ejectedName = "Nobody";
        if (ejectedId != null) {
            Player ejected = room.getPlayers().get(ejectedId);
            if (ejected != null) {
                ejected.setAlive(false);
                wasImposter = ejected.getRole() == PlayerRole.IMPOSTER;
                ejectedName = ejected.getName();
                room.setLastEjectedId(ejectedId);
                room.setLastEjectedWasImposter(wasImposter);
            }
        } else {
            room.setLastEjectedId(null);
        }

        broadcast(room.getRoomCode(), "VOTE_RESULT", Map.of(
                "ejectedName", ejectedName, "wasImposter", wasImposter));

        if (wasImposter) { endGame(room, "CREWMATES"); return; }
        if (checkImposterWin(room)) return;

        room.setRound(room.getRound() + 1);
        room.getChat().clear();
        schedule(room, GamePhase.TASK, 5);
    }

    // ── Win checks ─────────────────────────────────────────────────────────────

    private void checkCrewWin(GameRoom room) {
        long needed = room.getPlayers().values().stream()
                .filter(p -> p.getRole() == PlayerRole.CREWMATE && p.isAlive())
                .mapToLong(Player::getTotalTasks).sum();
        long done = room.getPlayers().values().stream()
                .filter(p -> p.getRole() == PlayerRole.CREWMATE && p.isAlive())
                .mapToLong(Player::getTasksCompleted).sum();
        if (needed > 0 && done >= needed) endGame(room, "CREWMATES");
    }

    private boolean checkImposterWin(GameRoom room) {
        long crew = room.getPlayers().values().stream()
                .filter(p -> p.isAlive() && p.getRole() == PlayerRole.CREWMATE).count();
        long imp = room.getPlayers().values().stream()
                .filter(p -> p.isAlive() && p.getRole() == PlayerRole.IMPOSTER).count();
        if (imp >= crew) { endGame(room, "IMPOSTERS"); return true; }
        return false;
    }

    private void endGame(GameRoom room, String winner) {
        cancelTimer(room);
        room.setPhase(GamePhase.ENDED);
        room.setWinner(winner);
        String impName = room.getPlayers().values().stream()
                .filter(p -> p.getRole() == PlayerRole.IMPOSTER)
                .map(Player::getName).findFirst().orElse("Unknown");
        broadcast(room.getRoomCode(), "GAME_ENDED", Map.of("winner", winner, "imposterName", impName));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void assignRolesAndTasks(GameRoom room) {
        List<String> ids = new ArrayList<>(room.getPlayers().keySet());
        Collections.shuffle(ids);
        int imposters = ids.size() >= 6 ? 2 : 1;
        for (int i = 0; i < ids.size(); i++) {
            Player p = room.getPlayers().get(ids.get(i));
            p.setRole(i < imposters ? PlayerRole.IMPOSTER : PlayerRole.CREWMATE);
            if (p.getRole() == PlayerRole.CREWMATE) {
                List<Task> tasks = taskService.generateTasksForPlayer();
                p.setTasks(tasks);
                p.setTotalTasks(tasks.size());
                p.setCurrentTaskIndex(0);
            }
            p.setAlive(true);
        }
    }

    private void schedule(GameRoom room, GamePhase next, int seconds) {
        ScheduledFuture<?> future = scheduler.schedule(
                () -> transitionTo(room, next), seconds, TimeUnit.SECONDS);
        room.setPhaseTimer(future);
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
        return Player.builder().id(id).name(name).color(color).alive(true).build();
    }

    private PlayerDto toDto(Player p, GameRoom room) {
        return PlayerDto.builder()
                .id(p.getId()).name(p.getName()).alive(p.isAlive())
                .tasksCompleted(p.getTasksCompleted()).totalTasks(p.getTotalTasks())
                .color(p.getColor()).isHost(p.getId().equals(room.getHostId()))
                .hasVoted(p.isHasVoted()).build();
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
