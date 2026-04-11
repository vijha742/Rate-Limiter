package com.vikas.rate_limiter.service;

import com.vikas.rate_limiter.RateLimitConfigEntity;
import com.vikas.rate_limiter.RateLimitConfigRepository;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MongoConfigurationStoreService {

    private final RateLimitConfigRepository repository;

    public List<RateLimitConfigEntity> getAllConfigs() {
        return repository.findAll();
    }

    public RateLimitConfigEntity saveConfig(RateLimitConfigEntity config) {
        return repository.save(config);
    }

    // NOTE: first check
    // public RateLimitConfigEntity getConfigByInfo() {
    // }

    public RateLimitConfigEntity getUserConfigWithApiKey(String api) {
        return repository.findById(api).orElse(null);
    }

    public Optional<RateLimitConfigEntity> getUserConfigWithIpAndEndpointAndUserTier(
            String ip, String endpoint, String userTier) {
        return repository.findByIpAndEndpointAndUserTier(ip, endpoint, userTier);
    }
}
