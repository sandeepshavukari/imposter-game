package com.game.imposter.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerDto {
    private String id;
    private String name;
    private boolean alive;
    private int tasksCompleted;
    private int totalTasks;
    private String color;
    private boolean isHost;
    private boolean hasVoted;
}
