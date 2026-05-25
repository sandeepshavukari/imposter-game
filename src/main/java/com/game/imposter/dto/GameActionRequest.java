package com.game.imposter.dto;

import lombok.Data;

@Data
public class GameActionRequest {
    private String roomCode;
    private String playerId;
    private String targetId;   // for voting
    private String clue;       // for clue submission
    private String guess;      // for imposter's word guess
}
