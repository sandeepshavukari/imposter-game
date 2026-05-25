package com.game.imposter.dto;

import lombok.Data;

@Data
public class JoinRoomRequest {
    private String roomCode;
    private String playerName;
}
