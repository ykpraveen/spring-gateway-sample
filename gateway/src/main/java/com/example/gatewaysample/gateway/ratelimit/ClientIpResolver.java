package com.example.gatewaysample.gateway.ratelimit;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves the client IP used to key the per-IP rate-limit bucket. {@code X-Forwarded-For} is
 * only honored when the direct TCP peer is a configured trusted proxy, so an untrusted client
 * can't spoof its way into a different IP bucket.
 *
 * <p>Trusted proxies accept both plain addresses and CIDR ranges (see {@link CidrRange}) rather
 * than only exact-address matches: a single literal address is only ever right for a gateway
 * process running directly on the host (loopback), since a container's Docker Compose deployment
 * sees host-originated traffic arrive from the bridge network's gateway address, not the loopback
 * address the trust check would otherwise expect — see {@code app.rate-limit.trusted-proxies} in
 * {@code application-local.yml}, which trusts the Compose network's whole CIDR for that reason.
 * Spring Security's {@code IpAddressMatcher} would do this too, but it implements the
 * servlet-based {@code RequestMatcher} interface, which isn't on this WebFlux-only module's
 * classpath.
 */
@Component
public class ClientIpResolver {

    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream().map(CidrRange::parse).toList();
    }

    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        String remoteIp = remoteAddress.getAddress().getHostAddress();

        if (isTrustedProxy(remoteIp)) {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return remoteIp;
    }

    private boolean isTrustedProxy(String remoteIp) {
        return trustedProxies.stream().anyMatch(range -> range.contains(remoteIp));
    }

    /** A parsed {@code address} or {@code address/prefixLength} entry, matched by masked bytes. */
    private record CidrRange(byte[] networkBytes, int prefixLength) {

        static CidrRange parse(String entry) {
            String[] parts = entry.split("/", 2);
            byte[] networkBytes = addressBytes(parts[0]);
            int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : networkBytes.length * 8;
            return new CidrRange(networkBytes, prefixLength);
        }

        boolean contains(String candidateIp) {
            byte[] candidateBytes = addressBytes(candidateIp);
            if (candidateBytes.length != networkBytes.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != candidateBytes[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (networkBytes[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
        }

        private static byte[] addressBytes(String address) {
            try {
                return InetAddress.getByName(address).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Not a valid IP address: " + address, e);
            }
        }
    }
}
