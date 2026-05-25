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
    private Map<String, String> votes = new ConcurrentHashMap<>();
    private List<ChatMessage> chat = new ArrayList<>();
    private boolean emergencyMeetingUsed = false;
    private boolean imposterKillUsed = false;
    private String winner;
    private String lastEjectedId;
    private boolean lastEjectedWasImposter;
    private long phaseEndsAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private ScheduledFuture<?> phaseTimer;
}
