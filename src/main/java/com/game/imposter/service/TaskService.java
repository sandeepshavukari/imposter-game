package com.game.imposter.service;

import com.game.imposter.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final List<Task> TASK_POOL = new ArrayList<>();

    static {
        // MATH tasks
        addTask(TaskType.MATH, "What is 7 × 8?", "56", "48", "54", "56", "63");
        addTask(TaskType.MATH, "What is 15 + 28?", "43", "41", "42", "43", "45");
        addTask(TaskType.MATH, "What is 64 ÷ 8?", "8", "6", "7", "8", "9");
        addTask(TaskType.MATH, "What is 12 × 12?", "144", "124", "132", "144", "156");
        addTask(TaskType.MATH, "What is 100 − 37?", "63", "53", "63", "67", "73");
        addTask(TaskType.MATH, "What is 9 × 9?", "81", "72", "81", "90", "99");
        addTask(TaskType.MATH, "What is 144 ÷ 12?", "12", "10", "11", "12", "13");
        addTask(TaskType.MATH, "What is 2 to the power of 8?", "256", "128", "256", "512", "1024");

        // COLOR tasks
        addTask(TaskType.COLOR, "What color is #FF0000?", "Red", "Red", "Blue", "Green", "Yellow");
        addTask(TaskType.COLOR, "What color is #0000FF?", "Blue", "Red", "Blue", "Green", "Purple");
        addTask(TaskType.COLOR, "What color is #FFFF00?", "Yellow", "Orange", "Yellow", "Green", "Gold");
        addTask(TaskType.COLOR, "What color is #00FF00?", "Green", "Blue", "Teal", "Green", "Cyan");
        addTask(TaskType.COLOR, "What color is #FF6600?", "Orange", "Red", "Yellow", "Orange", "Brown");
        addTask(TaskType.COLOR, "What color is #800080?", "Purple", "Pink", "Purple", "Violet", "Maroon");

        // TRIVIA tasks
        addTask(TaskType.TRIVIA, "How many planets in our solar system?", "8", "7", "8", "9", "10");
        addTask(TaskType.TRIVIA, "What is the capital of France?", "Paris", "London", "Berlin", "Paris", "Rome");
        addTask(TaskType.TRIVIA, "How many sides does a hexagon have?", "6", "5", "6", "7", "8");
        addTask(TaskType.TRIVIA, "What gas do plants absorb?", "CO2", "O2", "CO2", "N2", "H2");
        addTask(TaskType.TRIVIA, "How many continents are on Earth?", "7", "5", "6", "7", "8");
        addTask(TaskType.TRIVIA, "What is the fastest land animal?", "Cheetah", "Lion", "Cheetah", "Falcon", "Horse");
        addTask(TaskType.TRIVIA, "How many legs does a spider have?", "8", "6", "8", "10", "12");
        addTask(TaskType.TRIVIA, "What is H2O commonly known as?", "Water", "Salt", "Water", "Oxygen", "Acid");
    }

    private static void addTask(TaskType type, String question, String answer, String o1, String o2, String o3, String o4) {
        List<String> opts = Arrays.asList(o1, o2, o3, o4);
        Collections.shuffle(opts);
        TASK_POOL.add(Task.builder()
                .type(type).question(question).correctAnswer(answer)
                .options(new ArrayList<>(opts)).build());
    }

    public List<Task> generateTasksForPlayer() {
        List<Task> shuffled = new ArrayList<>(TASK_POOL);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(3, shuffled.size())).stream()
                .map(t -> Task.builder()
                        .id(UUID.randomUUID().toString())
                        .type(t.getType())
                        .question(t.getQuestion())
                        .correctAnswer(t.getCorrectAnswer())
                        .options(new ArrayList<>(t.getOptions()))
                        .completed(false)
                        .build())
                .collect(Collectors.toList());
    }
}
