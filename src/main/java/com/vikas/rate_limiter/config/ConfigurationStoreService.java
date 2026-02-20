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

    private final ConcurrentHashMap<String, RequestConfigDTO> userConfigMap = new ConcurrentHashMap<>();
    private final IpUtil ipUtility;
    private final ConcurrentHashMap<String, RateLimitAlgorithm> userAlgoMap = new ConcurrentHashMap<>();

    // TODO: Before storing config also check whether the configs are proper or
    // not..if not store
    // the default algorithm...
    public boolean storeConfigWithIP(HttpServletRequest req, RequestConfigDTO config) {
        if (req != null) {
            String ip = config.getIp() != null ? config.getIp() : ipUtility.getUserIp(req);
            if (ip != null && config != null) {
                this.userConfigMap.put(ip, config);
                log.info(
                        "Current status of algo_map after adding key-value pair {}",
                        this.userConfigMap);
                if (this.userAlgoMap.containsKey(ip)) {
                    this.userAlgoMap.remove(ip);
                }
                return true;
            } else
                return false;
        } else
            return false;
    }

    public RequestConfigDTO getConfigWithIP(String ip) {
        log.info("Current state of algo_map {}", this.userConfigMap);
        if (this.userConfigMap.containsKey(ip)) {
            return this.userConfigMap.get(ip);
        } else
            return null;
        // } else {
        // RequestConfigDTO config = new RequestConfigDTO();
        // config.setAlgo(Algorithm.FIXED_WINDOW);
        // Map<String, Integer> map = new HashMap<>();
        // map.put("windowSize", 60);
        // map.put("maxRequests", 10);
        // config.setParameters(map);
        // return config;
        // }
    }

    public RateLimitAlgorithm getAlgoWithIP(String ip) {
        if (this.userAlgoMap.containsKey(ip)) {
            return this.userAlgoMap.get(ip);
        } else
            return null;
    }

    public boolean setAlgoWithIP(String ip, RateLimitAlgorithm algo) {
        if (ip != null && !ip.isBlank()) {
            this.userAlgoMap.put(ip, algo);
            return true;
        } else
            return false;
    }
}
