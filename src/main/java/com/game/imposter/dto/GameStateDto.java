package com.game.imposter.dto;

import com.game.imposter.model.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameStateDto {
    private String roomCode;
    private GamePhase phase;
    private int round;
    private List<PlayerDto> players;

    // Personal info (only for this player)
    private PlayerRole myRole;
    private String myWord;         // player's own assigned word
    private boolean myClueGiven;   // has this player submitted a clue this round?

    // Clue feed (grows as players submit)
    private List<ClueEntry> allClues;

    // Voting
    private Map<String, Integer> voteCounts;

    // End-game reveals (only populated in RESULT phase)
    private String winner;
    private boolean imposterCaught;
    private String secretWord;          // crewmate word — revealed at RESULT
    private String imposterWord;        // imposter word  — revealed at RESULT
    private boolean imposterGuessCorrect;
    private String imposterName;        // revealed at RESULT

    private long phaseEndsAt;
    private String hostId;
}
