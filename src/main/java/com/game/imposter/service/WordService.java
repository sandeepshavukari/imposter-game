package com.game.imposter.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class WordService {

    private static final List<String[]> WORD_PAIRS = List.of(
        new String[]{"Blanket",     "Pillow"},
        new String[]{"Beach",       "Desert"},
        new String[]{"Coffee",      "Tea"},
        new String[]{"Cat",         "Dog"},
        new String[]{"Moon",        "Sun"},
        new String[]{"Piano",       "Guitar"},
        new String[]{"Ice cream",   "Cake"},
        new String[]{"Apple",       "Pear"},
        new String[]{"Rain",        "Snow"},
        new String[]{"Doctor",      "Nurse"},
        new String[]{"Sword",       "Knife"},
        new String[]{"Castle",      "Palace"},
        new String[]{"River",       "Lake"},
        new String[]{"Tiger",       "Lion"},
        new String[]{"Rose",        "Tulip"},
        new String[]{"Book",        "Newspaper"},
        new String[]{"Train",       "Bus"},
        new String[]{"Gold",        "Silver"},
        new String[]{"Spider",      "Ant"},
        new String[]{"Forest",      "Jungle"},
        new String[]{"Mountain",    "Hill"},
        new String[]{"Mirror",      "Window"},
        new String[]{"Lamp",        "Candle"},
        new String[]{"Bread",       "Rice"},
        new String[]{"Shirt",       "Jacket"},
        new String[]{"Chair",       "Sofa"},
        new String[]{"Crown",       "Hat"},
        new String[]{"Ocean",       "Sea"},
        new String[]{"Chocolate",   "Candy"},
        new String[]{"Basketball",  "Football"}
    );

    private final Random random = new Random();

    /**
     * Returns a random word pair: [0] = secret word (crewmates), [1] = imposter word.
     */
    public String[] getRandomPair() {
        return WORD_PAIRS.get(random.nextInt(WORD_PAIRS.size()));
    }
}
