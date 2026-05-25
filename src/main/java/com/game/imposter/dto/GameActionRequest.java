package com.game.imposter.dto;

import lombok.Data;

@Data
public class GameActionRequest {
    private String roomCode;
    private String playerId;
    private String targetId;
    private String message;
    private String answer;
}
