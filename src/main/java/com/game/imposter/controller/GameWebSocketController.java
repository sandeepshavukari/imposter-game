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

    @MessageMapping("/game/task/complete")
    public void completeTask(@Payload GameActionRequest req) {
        gameService.completeTask(req.getRoomCode(), req.getPlayerId(), req.getAnswer());
    }

    @MessageMapping("/game/eliminate")
    public void eliminate(@Payload GameActionRequest req) {
        gameService.eliminate(req.getRoomCode(), req.getPlayerId(), req.getTargetId());
    }

    @MessageMapping("/game/meeting")
    public void callMeeting(@Payload GameActionRequest req) {
        gameService.callMeeting(req.getRoomCode(), req.getPlayerId());
    }

    @MessageMapping("/game/chat")
    public void sendChat(@Payload GameActionRequest req) {
        gameService.sendChat(req.getRoomCode(), req.getPlayerId(), req.getMessage());
    }

    @MessageMapping("/game/vote")
    public void castVote(@Payload GameActionRequest req) {
        gameService.castVote(req.getRoomCode(), req.getPlayerId(), req.getTargetId());
    }
}
