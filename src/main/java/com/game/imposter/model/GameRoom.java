package com.game.imposter.model;

import lombok.*;
import java.util.*;
import java.util.concurrent.*;

@Data
public class GameRoom {
    private String roomCode;
    private Map<String, Player> players = new ConcurrentHashMap<>();
    private String hostId;
    private GamePhase phase = GamePhase.LOBBY;
    private int round = 0;

    // Word deduction fields
    private String secretWord;      // word given to crewmates
    private String imposterWord;    // word given to the imposter

    private List<ClueEntry> allClues = new ArrayList<>();
    private Map<String, String> votes = new ConcurrentHashMap<>();

    private String winner;
    private boolean imposterCaught;
    private String imposterGuessWord;
    private boolean imposterGuessCorrect;

    private long phaseEndsAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private ScheduledFuture<?> phaseTimer;
}
