package com.game.imposter.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {"/", "/lobby/**", "/game/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
