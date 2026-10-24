package dev.dailycareer.integration.slack;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class SlackEndpointPolicy {
    @FunctionalInterface interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final SlackSettings settings;
    private final Resolver resolver;

    @Autowired
    public SlackEndpointPolicy(SlackSettings settings) { this(settings, InetAddress::getAllByName); }

    SlackEndpointPolicy(SlackSettings settings, Resolver resolver) {
        this.settings = settings;
        this.resolver = resolver;
    }

    public URI validate(URI endpoint) {
        // The worker resolves an active personal or server-wide credential before validating its destination.
        if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null || !settings.allowsHost(endpoint.getHost())
                || (endpoint.getPort() != -1 && endpoint.getPort() != 443)
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null || endpoint.getQuery() != null
                || endpoint.getPath() == null || !endpoint.getPath().startsWith("/services/")) reject();
        try {
            InetAddress[] addresses = resolver.resolve(endpoint.getHost());
            if (addresses.length == 0) reject();
            for (InetAddress address : addresses) if (!publicAddress(address)) reject();
            return endpoint;
        } catch (UnknownHostException failure) {
            throw new IllegalArgumentException("Slack endpoint cannot be resolved");
        }
    }

    private static boolean publicAddress(InetAddress address) {
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

    private static void reject() { throw new IllegalArgumentException("Slack endpoint is not allowed"); }
}
