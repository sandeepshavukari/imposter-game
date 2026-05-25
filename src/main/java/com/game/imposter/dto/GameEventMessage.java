package com.game.imposter.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameEventMessage {
    private String type;
    private Object payload;
}
