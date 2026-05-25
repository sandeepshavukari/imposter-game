package com.game.imposter.model;

import lombok.*;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Player {
    private String id;
    private String name;
    private PlayerRole role;
    private boolean alive;
    private int tasksCompleted;
    private int totalTasks;
    private boolean hasVoted;
    private String color;
    private List<Task> tasks;
    private int currentTaskIndex;
}
