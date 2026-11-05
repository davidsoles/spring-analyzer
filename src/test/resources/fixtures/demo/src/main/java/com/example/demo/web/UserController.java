package com.example.demo.web;

import com.example.demo.ApiPaths;
import com.example.demo.User;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = ApiPaths.API + "/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    @GetMapping
    public List<User> list() {
        return List.of();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return new User(id, "demo");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public User create(@RequestBody User user) {
        return user;
    }

    @RequestMapping(value = {"/ping", "/health"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public String ping() {
        return "pong";
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
    }
}
