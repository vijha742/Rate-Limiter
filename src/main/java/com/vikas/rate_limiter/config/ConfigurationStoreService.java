package com.vikas.rate_limiter.config;

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

    private final ConcurrentHashMap<String, RequestConfigDTO> user_algo_map = new ConcurrentHashMap<>();

    public boolean storeConfigWithIP(HttpServletRequest req, RequestConfigDTO config) {
        if (req != null) {
            String ip = IpUtil.getUserIp(req);
            if (ip != null && config != null) {
                this.user_algo_map.put(ip, config);
                log.info(
                        "Current status of algo_map after adding key-value pair {}",
                        this.user_algo_map);
                return true;
            } else
                return false;
        } else
            return false;
    }

    public RequestConfigDTO getConfigWithIP(String ip) {
        log.info("Current state of algo_map {}", this.user_algo_map);
        if (this.user_algo_map.contains(ip)) {
            return this.user_algo_map.get(ip);
        } else
            return null;
    }
}
