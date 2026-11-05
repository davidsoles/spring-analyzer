package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test-only")
public class TestOnlyController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
