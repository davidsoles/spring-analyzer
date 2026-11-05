package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/iface")
public interface IfaceApi {

    @GetMapping("/ping")
    String ping();
}
