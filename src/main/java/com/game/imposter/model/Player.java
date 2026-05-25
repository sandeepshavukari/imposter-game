package com.game.imposter.model;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Player {
    private String id;
    private String name;
    private PlayerRole role;
    private String color;
    private String assignedWord;
    @Builder.Default
    private List<String> clues = new ArrayList<>();
    private int score;
    private boolean clueGivenThisRound;
    private boolean hasVoted;
}
