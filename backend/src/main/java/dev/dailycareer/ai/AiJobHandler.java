package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Feature-side contract for one AI job type. {@link #complete} and {@link #fail} run inside the worker's
 * transaction together with the job status and usage rows.
 */
public interface AiJobHandler {
    String jobType();

    AiRequest request(Job job);

    /** Validates and stores the provider result. Throw {@link IllegalArgumentException} to reject its quality. */
    void complete(Job job, JsonNode result);

    void fail(Job job, String errorCode);

    record Job(long id, long userId, String jobType, JsonNode input) {}
}
