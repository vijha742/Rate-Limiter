package com.vikas.rate_limiter.controllers;

import com.vikas.rate_limiter.RateLimitConfigEntity;
import com.vikas.rate_limiter.config.ConfigurationStoreService;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.service.MongoConfigurationStoreService;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TestController {

    private final ConfigurationStoreService configStore;
    private final MongoConfigurationStoreService mongoConfigStore;
    private final MeterRegistry registery;

    @GetMapping("/test")
    public String getGreets() {
        return "Hello Vikas Jha...!";
    }

    @GetMapping("/test/v2")
    public String getGreetings() {
        return "Hello Hero...!";
    }

    @PostMapping("/config")
    public boolean createConfig(
            @Valid @RequestBody RequestConfigDTO reqConfig, HttpServletRequest req) {
        // NOTE: One method in spring MVC allows only one parameter by
        // @RequestBody..if needed multiple parameters from req
        // body, create a DTO
        return configStore.storeConfigWithIP(req, reqConfig);
    }

    @PostMapping("/config/v2")
    public boolean createConfigV2(
            @Valid @RequestBody RateLimitConfigEntity config, HttpServletRequest req) {
        config.setCreatedAt(LocalDateTime.now());
        config.setLastAccessedAt(LocalDateTime.now());
        config.setAccessCount(1);
        mongoConfigStore.saveConfig(config);
        return true;
    }
}
