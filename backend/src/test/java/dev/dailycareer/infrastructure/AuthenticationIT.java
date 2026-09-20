package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.auth.ExpiringAuthorizationRequests;
import dev.dailycareer.auth.GoogleLogin;
import dev.dailycareer.user.UserAccountService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP + signed OIDC code exchange + PostgreSQL/JDBC sessions, no mock authentication. */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = {DailyCareerApplication.class, AuthenticationIT.ProviderConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "server.servlet.session.cookie.secure=false"})
class AuthenticationIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String, Grant> GRANTS = new ConcurrentHashMap<>();
    static final AtomicInteger EXCHANGES = new AtomicInteger();
    static final RSAKey KEY = key();
    static final HttpServer PROVIDER = provider();
    static final String ISSUER = "http://localhost:" + PROVIDER.getAddress().getPort();
    static final Set<String> ALLOWED = Set.of("flow@example.test", "reject@example.test", "concurrent@example.test", "suspended@example.test", "nickname@example.test", "isolation-a@example.test", "isolation-b@example.test");
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountService users;
    @LocalServerPort int port;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @AfterAll static void stopProvider() { PROVIDER.stop(0); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean @Primary GoogleLogin testGoogleLogin(JdbcTemplate jdbc, UserAccountService users) {
            var client = ClientRegistration.withRegistrationId("google").clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("{baseUrl}/login/oauth2/code/google").scope("openid", "email", "profile")
                    .authorizationUri(ISSUER + "/authorize").tokenUri(ISSUER + "/token").jwkSetUri(ISSUER + "/jwks")
                    .issuerUri(ISSUER).userNameAttributeName("sub").clientName("Test Google").build();
            return new GoogleLogin(new InMemoryClientRegistrationRepository(client), new ExpiringAuthorizationRequests(jdbc), users, ALLOWED);
        }
    }

    @Test void fullLoginProfileRevisionAndLogoutLifecycle() throws Exception {
        var browser = new Browser();
        var anonymous = browser.get("/api/v1/auth/csrf");
        assertThat(anonymous.statusCode()).isEqualTo(200);
        String oldToken = data(anonymous).get("token").asText();
        String oldCookie = browser.cookie();
        assertThat(anonymous.headers().firstValue("set-cookie").orElseThrow()).contains("CAREER_SESSION=", "Path=/", "HttpOnly", "SameSite=Lax").doesNotContain("Domain=");
        assertThat(anonymous.headers().firstValue("cache-control")).contains("no-store");
        var login = browser.login("flow@example.test", "flow-subject", "valid");
        assertRedirect(login, "/");
        assertThat(browser.cookie()).isNotEqualTo(oldCookie);
        var me = browser.get("/api/v1/users/me");
        assertThat(me.statusCode()).isEqualTo(200);
        JsonNode user = data(me);
        assertThat(user.get("userId").isTextual()).isTrue();
        assertThat(user.get("role").asText()).isEqualTo("USER");
        assertThat(user.get("curriculumId").isNull()).isTrue();
        assertThat(user.get("profileImageUrl").asText()).isEqualTo("https://example.test/profile.png");
        assertThat(user.get("joinedAt").asText()).endsWith("+09:00");
        String etag = me.headers().firstValue("etag").orElseThrow();
        assertThat(etag).isEqualTo("\"" + user.get("revision").asInt() + "\"");
        assertError(browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"changed\"}", oldToken, etag), 403, "CSRF_INVALID");
        String token = browser.csrf();
        assertError(browser.send("PATCH", "/api/v1/users/me?_csrf=" + encode(token), "{\"nickname\":\"changed\"}", null, etag), 403, "CSRF_INVALID");
        assertError(browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"changed\"}", token, null), 428, "PRECONDITION_REQUIRED");
        for (String invalid : List.of("{}", "{\"nickname\":null}", "{\"email\":\"other@example.test\"}", "{\"nickname\":123}", "{\"nickname\":\"ok\",\"role\":\"ADMIN\"}")) {
            assertError(browser.send("PATCH", "/api/v1/users/me", invalid, token, etag), 400, "INVALID_REQUEST");
        }
        for (String invalid : List.of(" ", "x".repeat(31))) {
            assertError(browser.send("PATCH", "/api/v1/users/me", JSON.writeValueAsString(Map.of("nickname", invalid)), token, etag), 400, "VALIDATION_FAILED");
        }
        var changed = browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"  새 이름  \"}", token, etag);
        assertThat(changed.statusCode()).isEqualTo(200);
        JsonNode changedUser = data(changed);
        assertThat(changedUser.get("nickname").asText()).isEqualTo("새 이름");
        assertThat(changedUser.get("revision").asInt()).isEqualTo(user.get("revision").asInt() + 1);
        String next = changed.headers().firstValue("etag").orElseThrow();
        assertThat(data(browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"새 이름\"}", token, next))).isEqualTo(changedUser);
        assertError(browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"stale\"}", token, etag), 412, "PRECONDITION_FAILED");
        // Session serialization must contain the app principal but no provider token/principal.
        String sessionId = new String(Base64.getDecoder().decode(browser.cookie()), StandardCharsets.UTF_8);
        byte[] bytes = jdbc.queryForObject("select a.attribute_bytes from public.spring_session_attributes a join public.spring_session s on s.primary_id=a.session_primary_id where s.session_id=? and a.attribute_name='SPRING_SECURITY_CONTEXT'", byte[].class, sessionId);
        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).contains("AppPrincipal").doesNotContain("OidcIdToken", "test-access-token", "DefaultOidcUser");
        assertError(browser.send("POST", "/api/v1/auth/logout", "{}", token, null), 400, "INVALID_REQUEST");
        assertError(browser.send("POST", "/api/v1/auth/logout", null, null, null), 403, "CSRF_INVALID");
        var logout = browser.send("POST", "/api/v1/auth/logout", null, token, null);
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(data(logout).get("loggedOut").asBoolean()).isTrue();
        assertThat(logout.headers().firstValue("set-cookie").orElseThrow()).contains("CAREER_SESSION=;", "Path=/", "Max-Age=0");
        assertThat(jdbc.queryForObject("select count(*) from public.spring_session where session_id=?", Integer.class, sessionId)).isZero();
        assertError(browser.get("/api/v1/users/me"), 401, "AUTH_REQUIRED");
        assertError(browser.send("POST", "/api/v1/auth/logout", null, token, null), 401, "AUTH_REQUIRED");
        assertThat(browser.get("/api/v1/auth/csrf").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest @ValueSource(strings = {"nonce", "issuer", "audience", "expired", "signature", "unverified", "unallowed"})
    void invalidProviderClaimsNeverCreateAUser(String mode) throws Exception {
        long before = jdbc.queryForObject("select count(*) from daily_career.app_user", Long.class);
        var browser = new Browser();
        assertRedirect(browser.login("reject@example.test", "reject-" + mode, mode),
                "/login?error=" + (Set.of("unverified", "unallowed").contains(mode) ? "ACCOUNT_NOT_ALLOWED" : "LOGIN_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from daily_career.app_user", Long.class)).isEqualTo(before);
        assertError(browser.get("/api/v1/users/me"), 401, "AUTH_REQUIRED");
    }

    @Test void sessionBoundStateExpiresAndCannotBeReplayedOrUsedInAnotherSession() throws Exception {
        var browser = new Browser();
        var start = browser.start();
        String callback = callback(start, "reject@example.test", "state-test", "valid");
        int before = EXCHANGES.get();
        assertRedirect(new Browser().get(callback), "/login?error=LOGIN_FAILED");
        assertThat(EXCHANGES.get()).isEqualTo(before);
        jdbc.update("update daily_career.oidc_login_state set created_at=now()-interval '11 minutes',expires_at=now()-interval '1 minute'");
        assertRedirect(browser.get(callback), "/login?error=LOGIN_FAILED");
        assertThat(EXCHANGES.get()).isEqualTo(before);
        var again = browser.start();
        String good = callback(again, "reject@example.test", "state-test", "valid");
        assertRedirect(browser.get(good), "/");
        int once = EXCHANGES.get();
        assertRedirect(browser.get(good), "/login?error=LOGIN_FAILED");
        assertThat(EXCHANGES.get()).isEqualTo(once);
    }

    @Test void malformedCallbacksAndReturnUrlAreRejectedBeforeTokenExchange() throws Exception {
        int before = EXCHANGES.get();
        for (String path : List.of("/oauth2/authorization/google?returnUrl=https://evil.example", "/login/oauth2/code/google",
                "/login/oauth2/code/google?state=1234567890123456&code=a&error=denied",
                "/login/oauth2/code/google?state=1234567890123456&code=a&code=b",
                "/login/oauth2/code/google?state=1234567890123456&state=1234567890123456&code=a")) {
            assertRedirect(new Browser().get(path), "/login?error=LOGIN_FAILED");
        }
        assertThat(EXCHANGES.get()).isEqualTo(before);
    }

    @Test void repeatedIdentityKeepsNicknameAndDifferentSubjectCannotClaimSameEmail() throws Exception {
        var browser = new Browser();
        assertRedirect(browser.login("nickname@example.test", "nickname-subject", "valid"), "/");
        var before = data(browser.get("/api/v1/users/me"));
        long id = before.get("userId").asLong();
        users.rename(id, before.get("revision").asInt(), "직접 정한 이름");
        assertRedirect(browser.login("nickname@example.test", "nickname-subject", "valid"), "/");
        assertThat(data(browser.get("/api/v1/users/me")).get("nickname").asText()).isEqualTo("직접 정한 이름");
        assertRedirect(new Browser().login("nickname@example.test", "different-subject", "valid"), "/login?error=ACCOUNT_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_identity where user_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void suspendedAccountLosesApiAccessAndCannotLogInAgain() throws Exception {
        var browser = new Browser();
        assertRedirect(browser.login("suspended@example.test", "suspended-subject", "valid"), "/");
        long id = data(browser.get("/api/v1/users/me")).get("userId").asLong();
        jdbc.update("update daily_career.app_user set status='SUSPENDED' where id=?", id);
        try {
            assertError(browser.get("/api/v1/users/me"), 401, "AUTH_REQUIRED");
            assertRedirect(browser.login("suspended@example.test", "suspended-subject", "valid"), "/login?error=ACCOUNT_NOT_ALLOWED");
        } finally { jdbc.update("update daily_career.app_user set status='ACTIVE' where id=?", id); }
    }

    @Test void concurrentNicknameEditsHaveOneWinner() throws Exception {
        var browser = new Browser();
        assertRedirect(browser.login("concurrent@example.test", "concurrent-subject", "valid"), "/");
        var me = browser.get("/api/v1/users/me");
        String etag = me.headers().firstValue("etag").orElseThrow(), token = browser.csrf();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"first\"}", token, etag).statusCode(); });
            var second = pool.submit(() -> { gate.await(); return browser.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"second\"}", token, etag).statusCode(); });
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
        }
    }

    @Test void concurrentlyLoadedSessionCopiesCanConsumeStateOnlyOnce() throws Exception {
        var repository = new ExpiringAuthorizationRequests(jdbc);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        String state = UUID.randomUUID().toString();
        var authorization = org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(ISSUER + "/authorize").clientId("test-client").redirectUri("http://localhost/callback").state(state).build();
        repository.saveAuthorizationRequest(authorization, request, response);
        var other = new org.springframework.mock.web.MockHttpServletRequest();
        var attributes = request.getSession().getAttributeNames();
        while (attributes.hasMoreElements()) { String name = attributes.nextElement(); other.getSession().setAttribute(name, request.getSession().getAttribute(name)); }
        request.setParameter("state", state);
        other.setParameter("state", state);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return repository.removeAuthorizationRequest(request, response) != null; });
            var second = pool.submit(() -> { gate.await(); return repository.removeAuthorizationRequest(other, new org.springframework.mock.web.MockHttpServletResponse()) != null; });
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void sessionsCannotSelectOrUpdateAnotherUser() throws Exception {
        var first = new Browser();
        var other = new Browser();
        assertRedirect(first.login("isolation-a@example.test", "isolation-a", "valid"), "/");
        assertRedirect(other.login("isolation-b@example.test", "isolation-b", "valid"), "/");
        var me = first.get("/api/v1/users/me");
        JsonNode mine = data(me), theirs = data(other.get("/api/v1/users/me"));
        assertThat(mine.get("userId")).isNotEqualTo(theirs.get("userId"));
        assertThat(data(first.get("/api/v1/users/me?userId=" + theirs.get("userId").asText()))).isEqualTo(mine);
        assertError(first.get("/api/v1/users/" + theirs.get("userId").asText()), 404, "RESOURCE_NOT_FOUND");
        String token = first.csrf(), etag = me.headers().firstValue("etag").orElseThrow();
        assertError(first.send("PATCH", "/api/v1/users/me", JSON.writeValueAsString(Map.of("nickname", "attacker", "userId", theirs.get("userId").asText())), token, etag), 400, "INVALID_REQUEST");
        assertThat(first.send("PATCH", "/api/v1/users/me", "{\"nickname\":\"my nickname\"}", token, etag).statusCode()).isEqualTo(200);
        assertThat(data(other.get("/api/v1/users/me"))).isEqualTo(theirs);
    }

    final class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, null, null); }
        HttpResponse<String> send(String method, String path, String body, String csrf, String etag) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) request.header("Content-Type", "application/json");
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (etag != null) request.header("If-Match", etag);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        Map<String, String> start() throws Exception {
            var response = get("/oauth2/authorization/google");
            assertThat(response.statusCode()).isEqualTo(302);
            URI destination = URI.create(response.headers().firstValue("location").orElseThrow());
            assertThat(destination.toString()).startsWith(ISSUER + "/authorize?");
            var query = parse(destination.getRawQuery());
            assertThat(query).containsEntry("code_challenge_method", "S256");
            assertThat(query.get("nonce")).isNotBlank();
            assertThat(query.get("state")).hasSizeGreaterThanOrEqualTo(16);
            return query;
        }
        HttpResponse<String> login(String email, String subject, String mode) throws Exception { return get(callback(start(), email, subject, mode)); }
        String csrf() throws Exception { return data(get("/api/v1/auth/csrf")).get("token").asText(); }
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("CAREER_SESSION")).findFirst().orElseThrow().getValue(); }
    }
    record Grant(String nonce, String challenge, String email, String subject, String mode) {}
    static String callback(Map<String, String> start, String email, String subject, String mode) {
        String code = UUID.randomUUID().toString();
        GRANTS.put(code, new Grant(start.get("nonce"), start.get("code_challenge"), email, subject, mode));
        return "/login/oauth2/code/google?code=" + code + "&state=" + encode(start.get("state"));
    }
    static JsonNode data(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()).get("data"); }
    static void assertRedirect(HttpResponse<String> response, String location) {
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains(location);
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        assertThat(response.body()).isEmpty();
    }
    static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).at("/error/code").asText()).isEqualTo(code);
    }
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static Map<String, String> parse(String query) {
        Map<String, String> values = new HashMap<>();
        for (String item : query.split("&")) { String[] pair = item.split("=", 2); values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8)); }
        return values;
    }
    static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] result = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result); exchange.close();
            });
            server.createContext("/token", exchange -> {
                try {
                    var body = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    Grant grant = GRANTS.remove(body.get("code"));
                    if (grant == null) throw new IllegalArgumentException("unknown grant");
                    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
                    if (!challenge.equals(grant.challenge())) throw new IllegalArgumentException("invalid PKCE");
                    EXCHANGES.incrementAndGet();
                    Instant now = Instant.now();
                    var claims = new JWTClaimsSet.Builder().issuer(grant.mode().equals("issuer") ? "https://wrong.example" : ISSUER)
                            .audience(grant.mode().equals("audience") ? "wrong-client" : "test-client").subject(grant.subject())
                            .issueTime(Date.from(now.minusSeconds(5))).expirationTime(Date.from(now.plusSeconds(grant.mode().equals("expired") ? -300 : 300)))
                            .claim("nonce", grant.mode().equals("nonce") ? "wrong-nonce" : grant.nonce())
                            .claim("email", grant.mode().equals("unallowed") ? "unknown@example.test" : grant.email())
                            .claim("email_verified", !grant.mode().equals("unverified"))
                            .claim("name", "테스트 사용자").claim("picture", "https://example.test/profile.png").build();
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
                    jwt.sign(new RSASSASigner(grant.mode().equals("signature") ? key() : KEY));
                    byte[] result = JSON.writeValueAsBytes(Map.of("access_token", "test-access-token", "token_type", "Bearer", "expires_in", 300, "id_token", jwt.serialize()));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result);
                } catch (Exception exception) { exchange.sendResponseHeaders(400, -1); }
                finally { exchange.close(); }
            });
            server.start(); return server;
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
