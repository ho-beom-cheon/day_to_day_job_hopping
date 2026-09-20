package dev.dailycareer.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.common.json.JsonConfiguration;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.common.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Test-only controller: no contract demonstration routes are shipped in production. */
@WebMvcTest(ApiContractTest.ContractController.class)
@Import({ApiContractTest.TestSecurity.class, ApiContractTest.ContractController.class, JsonConfiguration.class,
        ApiExceptionHandler.class, ApiErrorWriter.class, TraceIdFilter.class})
class ApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode schema(String name) throws Exception {
        try (var source = getClass().getResourceAsStream("/api/common-contract.json")) {
            return mapper.readTree(source).get("schemas").get(name);
        }
    }

    private void shape(JsonNode value, String schemaName) throws Exception {
        var expected = schema(schemaName);
        var required = new java.util.ArrayList<String>();
        expected.get("required").forEach(field -> required.add(field.textValue()));
        assertThat(value.propertyStream().map(java.util.Map.Entry::getKey).toList()).containsExactlyInAnyOrderElementsOf(required);
    }

    @TestConfiguration
    static class TestSecurity {
        @Bean @Order(0) SecurityFilterChain contractSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/v1/contract/**").authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
    }

    record Input(@JsonId @NotNull Long id, @NotBlank @Size(max = 30) String nickname,
                 @NotNull @Min(0) @Max(2147483647) Long revision) {}

    @RestController @RequestMapping("/api/v1/contract")
    static class ContractController {
        @PostMapping ResponseEntity<?> input(@Valid @RequestBody Input input, HttpServletRequest request) {
            return ApiResponses.ok(request, input);
        }
        @GetMapping("/empty") ResponseEntity<?> empty(HttpServletRequest request) { return ApiResponses.ok(request, null); }
        @GetMapping("/id/{id}") ResponseEntity<?> id(@PathVariable String id, HttpServletRequest request) {
            return ApiResponses.ok(request, new Input(Ids.parse(id), "fixture", 7L));
        }
        @GetMapping("/query") ResponseEntity<?> query(@RequestParam @Min(1) int page, HttpServletRequest request) {
            return ApiResponses.ok(request, page);
        }
        @PatchMapping("/revision") ResponseEntity<?> revision(HttpServletRequest request) {
            Revisions.requireMatch(Revisions.required(request), 7);
            return ApiResponses.success(request, 200, new Input(1L, "fixture", 8L), Revisions.etag(8), null);
        }
        @PostMapping("/key") ResponseEntity<?> key(HttpServletRequest request) {
            return ApiResponses.ok(request, IdempotencyService.requiredKey(request).toString());
        }
        @GetMapping("/error/{code}") ResponseEntity<?> error(@PathVariable String code) {
            throw new ApiException(ApiErrorCode.valueOf(code));
        }
        @GetMapping("/crash") void crash() { throw new IllegalStateException("sensitive SQL/token fixture"); }
        @GetMapping("/optimistic") void stale() { throw new org.springframework.dao.OptimisticLockingFailureException("private entity"); }
        @GetMapping("/many-errors") void many() {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, java.util.stream.IntStream.range(0, 120)
                    .mapToObj(i -> new ApiEnvelope.ValidationDetail("f".repeat(220), "m".repeat(600))).toList());
        }
    }

    @Test void successKeepsBigintStringNumericRevisionAndFreshMatchingMetadata() throws Exception {
        var first = mvc.perform(post("/api/v1/contract").with(csrf()).header("X-Trace-Id", "client-chosen")
                .contentType("application/json").content("{\"id\":\"9223372036854775807\",\"nickname\":\"fixture\",\"revision\":7}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = envelope(first);
        assertThat(body.get("success").booleanValue()).isTrue();
        assertThat(body.get("error").isNull()).isTrue();
        assertThat(body.at("/data/id").textValue()).isEqualTo("9223372036854775807");
        assertThat(body.at("/data/revision").isIntegralNumber()).isTrue();
        var second = mvc.perform(get("/api/v1/contract/empty")).andExpect(status().isOk()).andReturn();
        assertThat(envelope(second).get("data").isNull()).isTrue();
        assertThat(first.getResponse().getHeader("X-Trace-Id")).isNotEqualTo(second.getResponse().getHeader("X-Trace-Id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":1,\"nickname\":\"fixture\",\"revision\":7}",
            "{\"id\":\"1\",\"nickname\":\"fixture\",\"revision\":\"7\"}",
            "{\"id\":\"1\",\"nickname\":123,\"revision\":7}",
            "{\"id\":\"1\",\"nickname\":\"fixture\",\"revision\":7.5}",
            "{\"id\":\"1\",\"nickname\":\"fixture\",\"revision\":7,\"role\":\"ADMIN\"}",
            "{\"id\":\"1\",\"nickname\":\"fixture\",\"revision\":7,\"revision\":8}",
            "{\"id\":\"1\",\"nickname\":\"fixture\",\"revision\":7} {}", "{broken"
    })
    void rejectsMalformedUnknownDuplicateAndCoercedJson(String input) throws Exception {
        error(mvc.perform(post("/api/v1/contract").with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isBadRequest()).andReturn(), "INVALID_REQUEST");
    }

    @Test void validatesRequiredFieldsAndDoesNotExposeRejectedValues() throws Exception {
        var result = mvc.perform(post("/api/v1/contract").with(csrf()).contentType("application/json")
                .content("{\"id\":null,\"nickname\":\"\",\"revision\":2147483648}"))
                .andExpect(status().isBadRequest()).andReturn();
        JsonNode body = error(result, "VALIDATION_FAILED");
        assertThat(body.at("/error/details").size()).isEqualTo(3);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("2147483648", "rejectedValue", "Input(");
        error(mvc.perform(get("/api/v1/contract/query?page=0")).andExpect(status().isBadRequest()).andReturn(), "VALIDATION_FAILED");
        error(mvc.perform(get("/api/v1/contract/id/01")).andExpect(status().isBadRequest()).andReturn(), "INVALID_REQUEST");
    }

    @Test void conditionalWritesRequireOneValidCurrentEtag() throws Exception {
        error(mvc.perform(patch("/api/v1/contract/revision").with(csrf())).andExpect(status().is(428)).andReturn(), "PRECONDITION_REQUIRED");
        error(mvc.perform(patch("/api/v1/contract/revision").with(csrf()).header("If-Match", "\"6\""))
                .andExpect(status().is(412)).andReturn(), "PRECONDITION_FAILED");
        mvc.perform(patch("/api/v1/contract/revision").with(csrf()).header("If-Match", "\"7\""))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"8\""))
                .andExpect(jsonPath("$.data.revision").value(8));
        for (String value : List.of("*", "W/\"7\"", "7", "\"-1\"", "\"7\",\"8\"", "\"2147483648\"", "")) {
            error(mvc.perform(patch("/api/v1/contract/revision").with(csrf()).header("If-Match", value))
                    .andExpect(status().isBadRequest()).andReturn(), "INVALID_REQUEST");
        }
        error(mvc.perform(patch("/api/v1/contract/revision").with(csrf()).header("If-Match", "\"7\"", "\"7\""))
                .andExpect(status().isBadRequest()).andReturn(), "INVALID_REQUEST");
        assertThat(Revisions.parse("\"0\"")).isZero();
        assertThat(Revisions.parse("\"2147483647\"")).isEqualTo(Integer.MAX_VALUE);
        error(mvc.perform(get("/api/v1/contract/optimistic")).andExpect(status().is(412)).andReturn(), "PRECONDITION_FAILED");
    }

    @Test void idempotencyHeaderIsRequiredAndStrictlyUuid() throws Exception {
        error(mvc.perform(post("/api/v1/contract/key").with(csrf())).andExpect(status().isBadRequest()).andReturn(), "IDEMPOTENCY_KEY_REQUIRED");
        for (String value : List.of("", "1-1-1-1-1", "not-a-uuid", "00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000001")) {
            error(mvc.perform(post("/api/v1/contract/key").with(csrf()).header("Idempotency-Key", value))
                    .andExpect(status().isBadRequest()).andReturn(), "INVALID_REQUEST");
        }
        mvc.perform(post("/api/v1/contract/key").with(csrf()).header("Idempotency-Key", "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    }

    @Test void handlesUnexpectedErrorsAndRetryInstructionsWithoutSensitiveDetails() throws Exception {
        var crash = mvc.perform(get("/api/v1/contract/crash")).andExpect(status().isInternalServerError()).andReturn();
        error(crash, "INTERNAL_SERVER_ERROR");
        assertThat(crash.getResponse().getContentAsString()).doesNotContain("sensitive", "SQL", "token", "Exception", "stackTrace");
        var busy = mvc.perform(get("/api/v1/contract/error/REQUEST_IN_PROGRESS"))
                .andExpect(status().isConflict()).andExpect(header().string("Retry-After", "1")).andReturn();
        assertThat(error(busy, "REQUEST_IN_PROGRESS").at("/error/retryAfterSeconds").intValue()).isEqualTo(1);
        var errors = mvc.perform(get("/api/v1/contract/many-errors")).andExpect(status().isBadRequest()).andReturn();
        var details = error(errors, "VALIDATION_FAILED").at("/error/details");
        assertThat(details.size()).isEqualTo(100);
        assertThat(details.get(0).get("field").textValue()).hasSize(200);
        assertThat(details.get(0).get("message").textValue()).hasSize(500);
    }

    @Test void replayPreservesResourceHeadersButRegeneratesTraceAndTime() throws Exception {
        var reply = new StoredReply(202, mapper.readTree("{\"jobId\":\"9223372036854775807\"}"), "\"3\"", "/api/v1/ai/jobs/1");
        var first = reply.toResponse(new org.springframework.mock.web.MockHttpServletRequest());
        var second = reply.toResponse(new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(second.getStatusCode().value()).isEqualTo(202);
        assertThat(second.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(second.getHeaders().getLocation().toString()).isEqualTo("/api/v1/ai/jobs/1");
        assertThat(second.getBody().meta().traceId()).isNotEqualTo(first.getBody().meta().traceId());
    }

    private JsonNode envelope(MvcResult result) throws Exception {
        var response = result.getResponse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        shape(body, "ErrorEnvelope"); // All 88 success wrappers have the same four required properties.
        shape(body.get("meta"), "Meta");
        assertThat(body.at("/meta/traceId").textValue()).matches(schema("Meta").at("/properties/traceId/pattern").textValue())
                .isEqualTo(response.getHeader("X-Trace-Id"));
        assertThat(OffsetDateTime.parse(body.at("/meta/serverTime").textValue()).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        return body;
    }

    private JsonNode error(MvcResult result, String code) throws Exception {
        JsonNode body = envelope(result);
        assertThat(body.get("success").booleanValue()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        shape(body.get("error"), "Error");
        assertThat(body.at("/error/code").textValue()).isEqualTo(code);
        assertThat(body.at("/error/message").textValue()).isNotBlank().hasSizeLessThanOrEqualTo(500);
        assertThat(body.at("/error/details").isArray()).isTrue();
        for (var detail : body.at("/error/details")) shape(detail, "ValidationDetail");
        assertThat(body.at("/error/retryable").isBoolean()).isTrue();
        return body;
    }
}
