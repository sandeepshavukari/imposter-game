package com.game.imposter.model;

import lombok.*;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    private String id;
    private TaskType type;
    private String question;
    private String correctAnswer;
    private List<String> options;
    private boolean completed;
}
