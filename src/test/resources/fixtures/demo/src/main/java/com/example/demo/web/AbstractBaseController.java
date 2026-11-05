package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/base")
public abstract class AbstractBaseController {

    @GetMapping("/common")
    public String common() {
        return "common";
    }
}
