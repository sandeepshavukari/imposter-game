package com.game.imposter.model;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    private String senderId;
    private String senderName;
    private String message;
    private long timestamp;
}
