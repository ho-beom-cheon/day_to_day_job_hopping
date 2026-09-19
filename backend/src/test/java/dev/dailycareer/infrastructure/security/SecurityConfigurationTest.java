package dev.dailycareer.infrastructure.security;

import dev.dailycareer.common.trace.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest
@Import({SecurityConfiguration.class, TraceIdFilter.class})
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;

    @Test void unimplementedApiIsClosedAndNotRedirectedToInventedLogin() throws Exception {
        mvc.perform(get("/api/v1/contract-test-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(content().string(""));
    }

    @Test void writeWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/contract-test-only")).andExpect(status().isForbidden());
    }

    @Test void authenticatedUserCannotAccessUnimplementedRoutes() throws Exception {
        mvc.perform(post("/api/v1/contract-test-only").with(user("test")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void defaultLoginAndLogoutAreNotExposed() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(post("/logout").with(csrf())).andExpect(status().isUnauthorized());
    }
}
