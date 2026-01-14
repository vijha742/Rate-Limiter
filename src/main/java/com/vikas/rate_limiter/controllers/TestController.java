package com.vikas.rate_limiter.controllers;

import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TestController {

    private final ConfigurationStoreService configStore;

    @GetMapping("/test")
    public String getGreets() {
        return "Hello Vikas Jha...!";
    }

    @PostMapping("/config")
    public boolean createConfig(
            @Valid @RequestBody RequestConfigDTO reqConfig, HttpServletRequest req) {
        // NOTE: One method in spring MVC allows only one parameter by
        // @RequestBody..if needed multiple parameters from req
        // body, create a DTO
        return configStore.storeConfigWithIP(req, reqConfig);
    }
}
