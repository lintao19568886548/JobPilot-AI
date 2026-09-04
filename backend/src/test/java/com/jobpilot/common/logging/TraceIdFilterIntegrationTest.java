package com.jobpilot.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterIntegrationTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void propagatesValidClientTraceIdIntoHeaderAndApiBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "trace-client-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final ApiResponse<?>[] body = new ApiResponse<?>[1];
        FilterChain chain = (req, res) -> body[0] = ApiResponse.success("ok");
        filter.doFilter(request, response, chain);
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("trace-client-1234");
        assertThat(body[0].traceId()).isEqualTo("trace-client-1234");
    }

    @Test
    void replacesUnsafeTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "unsafe trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(response.getHeader(TraceIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
