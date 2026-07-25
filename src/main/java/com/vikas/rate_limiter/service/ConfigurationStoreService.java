package com.vikas.rate_limiter.service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConfigurationStoreService {

    private final MongoConfigurationStoreService dbService;
}
