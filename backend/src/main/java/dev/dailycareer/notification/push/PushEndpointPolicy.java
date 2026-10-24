package dev.dailycareer.notification.push;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PushEndpointPolicy {
    @FunctionalInterface interface Resolver { InetAddress[] resolve(String host) throws Exception; }
    private final WebPushSettings settings;
    private final Resolver resolver;

    @Autowired
    public PushEndpointPolicy(WebPushSettings settings) { this(settings, InetAddress::getAllByName); }
    PushEndpointPolicy(WebPushSettings settings, Resolver resolver) { this.settings = settings; this.resolver = resolver; }

    public URI validate(String endpoint) {
        try {
            if (endpoint == null || endpoint.length() > 2048) reject();
            URI uri = URI.create(endpoint).normalize();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !allowed(host.toLowerCase())) reject();
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !publicAddress(address))) reject();
            return uri;
        } catch (ApiException expected) {
            throw expected;
        } catch (Exception invalid) {
            throw new ApiException(ApiErrorCode.PUSH_ENDPOINT_REJECTED);
        }
    }

    private boolean allowed(String host) {
        return settings.allowedHosts().stream().anyMatch(entry -> entry.startsWith(".")
                ? host.endsWith(entry) && host.length() > entry.length() : host.equals(entry));
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first = raw[0] & 255, second = raw[1] & 255;
            return first != 0 && first < 224 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        return (raw[0] & 0xfe) != 0xfc;
    }

    private static void reject() { throw new ApiException(ApiErrorCode.PUSH_ENDPOINT_REJECTED); }
}
