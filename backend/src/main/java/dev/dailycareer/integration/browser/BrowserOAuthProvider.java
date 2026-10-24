package dev.dailycareer.integration.browser;

import com.fasterxml.jackson.databind.JsonNode;

public interface BrowserOAuthProvider {
    Grant exchange(String provider, String code, String verifier) throws Exception;
    record Grant(JsonNode secret, JsonNode metadata) {
        @Override public String toString() { return "Grant[redacted]"; }
    }
}
