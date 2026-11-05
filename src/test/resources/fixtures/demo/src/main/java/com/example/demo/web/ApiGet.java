package com.example.demo.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.GetMapping;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@GetMapping
public @interface ApiGet {

    String[] value() default {};

    String[] path() default {};

    String[] produces() default {};
}
