package dev.dailycareer.infrastructure.security;

import dev.dailycareer.common.trace.TraceIdFilter;
import dev.dailycareer.common.api.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(controllers = {dev.dailycareer.user.UserController.class, dev.dailycareer.auth.AuthController.class})
@Import({SecurityConfiguration.class, TraceIdFilter.class, ApiErrorWriter.class})
class SecurityConfigurationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean dev.dailycareer.user.UserAccountService users;
    @Autowired MockMvc mvc;

    @Test void unimplementedApiIsClosedAndNotRedirectedToInventedLogin() throws Exception {
        mvc.perform(get("/api/v1/contract-test-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.meta.traceId").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")));
    }

    @Test void writeWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/contract-test-only")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test void authenticatedUserCannotAccessUnimplementedRoutes() throws Exception {
        mvc.perform(post("/api/v1/contract-test-only").with(user("test")).with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test void defaultLoginAndLogoutAreNotExposed() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(post("/logout").with(csrf().asHeader())).andExpect(status().isUnauthorized());
    }
}
