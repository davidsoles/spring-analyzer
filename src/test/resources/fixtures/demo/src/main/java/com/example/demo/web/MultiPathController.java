package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/multi-a", "/multi-b"})
public class MultiPathController {

    @GetMapping({"/x", "/y"})
    public String xy() {
        return "xy";
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    public String any() {
        return "any";
    }
}
