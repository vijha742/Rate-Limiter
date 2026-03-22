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

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, RequestConfigDTO>> userConfigMap = new ConcurrentHashMap<>();
    private final IpUtil ipUtility;
    private final ConcurrentHashMap<String, RateLimitAlgorithm> userAlgoMap = new ConcurrentHashMap<>();

    // TODO: Before storing config also check whether the configs are proper or
    // not..if not store
    // the default algorithm...
    public boolean storeConfigWithIP(HttpServletRequest req, RequestConfigDTO config) {
        if (req != null) {
            String ip = config.getIp() != null ? config.getIp() : ipUtility.getUserIp(req);
            if (ip != null && config != null) {
                String uri = config.getEndpoint() != null ? config.getEndpoint() : req.getRequestURI();
                this.userConfigMap
                        .computeIfAbsent(ip, k -> new ConcurrentHashMap<>())
                        .put(uri, config);
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

    public RequestConfigDTO getConfigWithIPAndUri(String ip, String uri) {
        ConcurrentHashMap<String, RequestConfigDTO> uriMap = this.userConfigMap.get(ip);
        if (uriMap != null) {
            if (uriMap.containsKey(uri)) {
                return (RequestConfigDTO) uriMap.get(uri);
            } else
                return null;
        } else
            return null;
    }

    public RequestConfigDTO getConfigWithIP(String ip) {
        log.info("Current state of algo_map {}", this.userConfigMap);
        if (this.userConfigMap.containsKey(ip)) {
            ConcurrentHashMap<String, RequestConfigDTO> uriMap = this.userConfigMap.get(ip);
            if (uriMap != null && !uriMap.isEmpty()) {
                // Return the first config for this IP (you may want to implement a default URI
                // strategy)
                return uriMap.values().iterator().next();
            }
        }
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
