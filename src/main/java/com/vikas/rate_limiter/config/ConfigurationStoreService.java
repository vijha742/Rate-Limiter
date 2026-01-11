package com.vikas.rate_limiter.config;

import com.vikas.rate_limiter.algorithm.RateLimitAlgorithm;
import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.utils.IpUtil;

import jakarta.servlet.http.HttpServletRequest;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
@Component
public class ConfigurationStoreService {

    private final ConcurrentHashMap<String, RequestConfigDTO> user_config_map = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, RateLimitAlgorithm> user_algo_map = new ConcurrentHashMap<>();

    // TODO: Before storing config also check whether the configs are proper or
    // not..if not store
    // the default algorithm...
    public boolean storeConfigWithIP(HttpServletRequest req, RequestConfigDTO config) {
        if (req != null) {
            String ip = config.getIp() != null ? config.getIp() : IpUtil.getUserIp(req);
            if (ip != null && config != null) {
                this.user_config_map.put(ip, config);
                log.info(
                        "Current status of algo_map after adding key-value pair {}",
                        this.user_config_map);
                if (this.user_algo_map.containsKey(ip)) {
                    this.user_algo_map.remove(ip);
                }
                return true;
            } else
                return false;
        } else
            return false;
    }

    public RequestConfigDTO getConfigWithIP(String ip) {
        log.info("Current state of algo_map {}", this.user_config_map);
        if (this.user_config_map.containsKey(ip)) {
            return this.user_config_map.get(ip);
        } else
            return null;
    }

    public RateLimitAlgorithm getAlgoWithIP(String ip) {
        if (this.user_algo_map.containsKey(ip)) {
            return this.user_algo_map.get(ip);
        } else
            return null;
    }

    public boolean setAlgoWithIP(String ip, RateLimitAlgorithm algo) {
        if (ip != null && !ip.isBlank()) {
            this.user_algo_map.put(ip, algo);
            return true;
        } else
            return false;
    }
}
