package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;

public interface UserApi {

    @GetMapping("/from-interface")
    String fromInterface();
}
