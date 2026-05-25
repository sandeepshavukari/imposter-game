package com.game.imposter.dto;

import com.game.imposter.model.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameStateDto {
    private String roomCode;
    private GamePhase phase;
    private int round;
    private List<PlayerDto> players;
    private PlayerRole myRole;
    private Task currentTask;
    private List<ChatMessage> chat;
    private Map<String, Integer> voteCounts;
    private String winner;
    private String lastEjectedName;
    private boolean lastEjectedWasImposter;
    private String imposterName;
    private long phaseEndsAt;
    private boolean emergencyMeetingUsed;
    private boolean imposterKillUsed;
    private String hostId;
}
