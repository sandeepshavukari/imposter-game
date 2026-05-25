package com.game.imposter.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerDto {
    private String id;
    private String name;
    private String color;
    private boolean host;
    private boolean hasVoted;
    private boolean clueGivenThisRound;
    private int score;
    private List<String> clues; // visible to all (for voting/result display)
}
