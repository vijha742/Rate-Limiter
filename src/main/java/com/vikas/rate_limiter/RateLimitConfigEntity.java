package com.vikas.rate_limiter;

import com.vikas.rate_limiter.dto.RequestConfigDTO.Algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "rate_limit_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndex(def = "{'ip': 1, 'endpoint': 1, 'userTier': 1}", name = "ip_endpoint_idx")
public class RateLimitConfigEntity {

    @Id private String id;

    @Indexed private String ip;

    @Indexed private String endpoint;

    private String userTier;

    private String method; // Store as String for MongoDB

    private Algorithm algorithm; // Store enum as String

    private Map<String, Integer> parameters;

    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt; // For cache eviction
    private Integer accessCount; // For LRU/LFU
}
