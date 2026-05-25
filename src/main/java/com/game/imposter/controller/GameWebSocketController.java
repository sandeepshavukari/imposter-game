package com.game.imposter.controller;

import com.game.imposter.dto.GameActionRequest;
import com.game.imposter.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final GameService gameService;

    @MessageMapping("/game/start")
    public void startGame(@Payload GameActionRequest req) {
        gameService.startGame(req.getRoomCode(), req.getPlayerId());
    }

    @MessageMapping("/game/clue")
    public void submitClue(@Payload GameActionRequest req) {
        gameService.submitClue(req.getRoomCode(), req.getPlayerId(), req.getClue());
    }

    @MessageMapping("/game/vote")
    public void castVote(@Payload GameActionRequest req) {
        gameService.castVote(req.getRoomCode(), req.getPlayerId(), req.getTargetId());
    }

    @MessageMapping("/game/guess")
    public void submitGuess(@Payload GameActionRequest req) {
        gameService.submitGuess(req.getRoomCode(), req.getPlayerId(), req.getGuess());
    }
}
