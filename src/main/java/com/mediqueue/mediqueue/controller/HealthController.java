package com.mediqueue.mediqueue.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> heath()
    {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app","MediQueue",
                "message","Hospital OPS system is running"
        ));
    }
}
