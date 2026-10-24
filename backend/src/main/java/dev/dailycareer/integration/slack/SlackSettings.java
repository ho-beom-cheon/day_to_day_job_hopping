package dev.dailycareer.integration.slack;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class SlackSettings {
    private static final Set<String> DEFAULT_HOSTS = Set.of("hooks.slack.com", "hooks.slack-gov.com");
    private dev.dailycareer.integration.browser.BrowserConnections connections;
    private final String credentialRef;
    private final String webhookUrl;
    private final String channelName;
    private final Set<String> allowedHosts;

    public SlackSettings(@Value("${app.slack.credential-ref:}") String credentialRef,
                         @Value("${app.slack.webhook-url:}") String webhookUrl,
                         @Value("${app.slack.channel-name:}") String channelName,
                         @Value("${app.slack.allowed-hosts:}") String allowedHosts) {
        this.credentialRef = trim(credentialRef);
        this.webhookUrl = trim(webhookUrl);
        this.channelName = trim(channelName);
        var configured = new LinkedHashSet<String>();
        Arrays.stream(trim(allowedHosts).split(",")).map(String::trim).map(String::toLowerCase)
                .filter(value -> !value.isBlank()).forEach(configured::add);
        this.allowedHosts = configured.isEmpty() ? DEFAULT_HOSTS : Set.copyOf(configured);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SlackSettings(@Value("${app.slack.credential-ref:}") String ref,
                         @Value("${app.slack.webhook-url:}") String url,
                         @Value("${app.slack.channel-name:}") String channel,
                         @Value("${app.slack.allowed-hosts:}") String hosts,
                         dev.dailycareer.integration.browser.BrowserConnections connections) {
        this(ref, url, channel, hosts);
        this.connections = connections;
    }
    public boolean enabled(long user) { return connections != null && connections.reference(user, "slack") != null || enabled(); }
    public String credentialRef(long user) {
        String personal = connections == null ? null : connections.reference(user, "slack");
        return personal != null ? personal : credentialRef;
    }

    public boolean enabled() {
        if (credentialRef.isBlank() || webhookUrl.isBlank()) return false;
        try {
            URI uri = URI.create(webhookUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && allowedHosts.contains(uri.getHost().toLowerCase())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null && uri.getFragment() == null && uri.getQuery() == null
                    && uri.getPath() != null && uri.getPath().startsWith("/services/");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public String credentialRef() { return credentialRef; }
    public String channelName() { return channelName.isBlank() ? null : channelName; }
    public boolean allowsHost(String host) { return host != null && allowedHosts.contains(host.toLowerCase()); }

    public URI resolve(String reference) {
        if (enabled() && credentialRef.equals(reference)) return URI.create(webhookUrl);
        if (connections != null) return URI.create(connections.slackWebhook(reference));
        throw new IllegalStateException("Slack credential is unavailable");
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
