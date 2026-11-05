package com.example.demo.web;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class IfaceController implements IfaceApi {

    @Override
    public String ping() {
        return "pong";
    }
}
