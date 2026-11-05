package com.example.demo.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiRestController
@RequestMapping("/meta")
public class MetaController {

    @ApiGet("/hello")
    public String hello() {
        return "hello";
    }

    @ApiGet(path = "/bye", produces = MediaType.TEXT_PLAIN_VALUE)
    public String bye() {
        return "bye";
    }
}
