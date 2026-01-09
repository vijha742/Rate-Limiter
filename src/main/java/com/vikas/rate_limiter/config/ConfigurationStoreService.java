package com.vikas.rate_limiter.config;

import com.vikas.rate_limiter.dto.RequestConfigDTO;
import com.vikas.rate_limiter.utils.IpUtil;

import jakarta.servlet.http.HttpServletRequest;

import lombok.Data;

import java.util.concurrent.ConcurrentHashMap;

@Data
public class ConfigurationStoreService {

    private ConcurrentHashMap<String, RequestConfigDTO> user_algo_map;

    public boolean storeConfigWithIP(HttpServletRequest req, RequestConfigDTO config) {
        if (req != null) {
            String ip = IpUtil.getUserIp(req);
            if (ip != null && config != null) {
                this.user_algo_map.put(ip, config);
                return true;
            } else
                return false;
        } else
            return false;
    }

    public RequestConfigDTO getConfigWithIP(String ip) {
        if (this.user_algo_map.contains(ip)) {
            return this.user_algo_map.get(ip);
        } else
            return null;
    }
}
