package com.vikas.rate_limiter;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends MongoRepository<RateLimitConfigEntity, String> {

    Optional<RateLimitConfigEntity> findByIpAndEndpointAndUserTier(
            String ip, String endpoint, String userTier);
}
