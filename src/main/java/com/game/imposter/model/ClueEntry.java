package com.game.imposter.model;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClueEntry {
    private String playerId;
    private String playerName;
    private String color;
    private String word;
    private int round;
}
