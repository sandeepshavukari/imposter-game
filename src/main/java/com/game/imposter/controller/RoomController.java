package com.game.imposter.controller;

import com.game.imposter.dto.*;
import com.game.imposter.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class RoomController {

    private final GameService gameService;

    @PostMapping("/create")
    public RoomResponse createRoom(@RequestBody CreateRoomRequest req) {
        return gameService.createRoom(req.getPlayerName());
    }

    @PostMapping("/join")
    public RoomResponse joinRoom(@RequestBody JoinRoomRequest req) {
        return gameService.joinRoom(req.getRoomCode(), req.getPlayerName());
    }

    @GetMapping("/{roomCode}/state")
    public GameStateDto getState(@PathVariable String roomCode,
                                  @RequestParam String playerId) {
        return gameService.getState(roomCode, playerId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleError(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
