package com.example.demo.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inherited")
public class InheritedController implements UserApi {

    @Override
    public String fromInterface() {
        return "ok";
    }
}
