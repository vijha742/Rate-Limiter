package com.vikas.rate_limiter.controllers;

import com.vikas.rate_limiter.dto.RequestConfigDTO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/test")
    public String getGreets() {
        return "Hello Vikas Jha...!";
    }

    @PutMapping("/config")
    public boolean createConfig(@RequestBody RequestConfigDTO reqConfig, HttpServletRequest req) {
        // One method in spring MVC allows only one parameter by
        // @RequestBody..if needed multiple parameters from req
        // body, create a DTO
        return true;
    }
}
