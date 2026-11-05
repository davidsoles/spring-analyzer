package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/view")
    public String view() {
        return "admin/view";
    }

    @ResponseBody
    @GetMapping("/status")
    public String status() {
        return "ok";
    }
}
