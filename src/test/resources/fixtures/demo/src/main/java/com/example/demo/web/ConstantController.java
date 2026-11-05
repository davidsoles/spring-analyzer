package com.example.demo.web;

import com.example.demo.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.V1 + "/items")
public class ConstantController {

    @GetMapping(ApiPaths.ITEM_ID)
    public String item() {
        return "item";
    }
}
