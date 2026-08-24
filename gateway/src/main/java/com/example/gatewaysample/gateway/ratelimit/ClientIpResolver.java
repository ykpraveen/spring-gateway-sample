package com.example.gatewaysample.gateway.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves the client IP used to key the per-IP rate-limit bucket. {@code X-Forwarded-For} is
 * only honored when the direct TCP peer is a configured trusted proxy, so an untrusted client
 * can't spoof its way into a different IP bucket.
 */
@Component
public class ClientIpResolver {

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String remoteIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";

        if (properties.trustedProxies().contains(remoteIp)) {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return remoteIp;
    }
}
