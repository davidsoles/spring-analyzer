package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class Outer {

    @RestController
    @RequestMapping("/nested")
    public static class NestedController {

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
