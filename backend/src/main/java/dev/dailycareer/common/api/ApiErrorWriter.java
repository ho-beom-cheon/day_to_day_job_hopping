package dev.dailycareer.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Security filters run before ControllerAdvice, so share the same response factory here. */
@Component
public final class ApiErrorWriter {
    private final ObjectMapper mapper;
    public ApiErrorWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, ApiErrorCode code) throws IOException {
        if (response.isCommitted()) return;
        var result = ApiResponses.failure(request, new ApiException(code));
        response.setStatus(result.getStatusCode().value());
        response.setCharacterEncoding("UTF-8");
        result.getHeaders().forEach((name, values) -> response.setHeader(name, String.join(", ", values)));
        mapper.writeValue(response.getOutputStream(), result.getBody());
    }
}
