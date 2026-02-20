package com.vikas.rate_limiter.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class IpUtil {

    private static final Logger logger = LoggerFactory.getLogger(IpUtil.class);

    private static final String[] PROXY_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "CF-Connecting-IP"
    };

    private final RateLimiterProperties properties;

    public IpUtil(RateLimiterProperties properties) {
        this.properties = properties;
    }

    public String getUserIp(HttpServletRequest req) {
        if (!isSecurityEnabled()) {
            return req.getRemoteAddr();
        }

        String remoteAddr = req.getRemoteAddr();
        
        if (!isFromTrustedProxy(req)) {
            if (shouldLogSuspicious()) {
                logger.warn("Request not from trusted proxy. Using remote address: {}", remoteAddr);
            }
            return remoteAddr;
        }

        for (String header : PROXY_HEADERS) {
            String val = req.getHeader(header);
            if (val != null && !val.isEmpty() && !"unknown".equalsIgnoreCase(val)) {
                String[] ips = val.split(",");
                String clientIp = ips[0].trim();

                if (!isValidIpFormat(clientIp)) {
                    if (shouldLogSuspicious()) {
                        logger.warn("Invalid IP format in header {}: {}", header, clientIp);
                    }
                    continue;
                }

                if (isPrivateIp(clientIp) && shouldLogSuspicious()) {
                    logger.warn("Private IP in header {} (possible spoofing): {}", header, clientIp);
                }

                return clientIp;
            }
        }

        return remoteAddr;
    }

    private boolean isSecurityEnabled() {
        return properties != null && 
               properties.getSecurity() != null && 
               properties.getSecurity().isEnabled();
    }

    private boolean isFromTrustedProxy(HttpServletRequest req) {
        if (properties == null || properties.getSecurity() == null) {
            return false;
        }

        String remoteAddr = req.getRemoteAddr();
        List<String> trustedProxies = properties.getSecurity().getTrustedProxies();

        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }

        for (String proxy : trustedProxies) {
            if (isIpInRange(remoteAddr, proxy)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidIpFormat(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        try {
            if (ip.contains(".")) {
                String[] parts = ip.split("\\.");
                if (parts.length != 4) {
                    return false;
                }
                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
            } else if (ip.contains(":")) {
                InetAddress.getByName(ip);
            }
            return true;
        } catch (NumberFormatException | UnknownHostException e) {
            return false;
        }
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }

        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isSiteLocalAddress() || 
                   addr.isLoopbackAddress() || 
                   addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isIpInRange(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return ip.equals(cidr);
            }

            String[] parts = cidr.split("/");
            String cidrIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress cidrAddr = InetAddress.getByName(cidrIp);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] cidrBytes = cidrAddr.getAddress();

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != cidrBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < ipBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((ipBytes[fullBytes] & mask) != (cidrBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean shouldLogSuspicious() {
        return properties != null && 
               properties.getSecurity() != null && 
               properties.getSecurity().isLogSuspicious();
    }
}
