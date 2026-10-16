package com.milind.employeeagent.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private final ApiKeyFilter filter = new ApiKeyFilter("secret");

    private int call(String uri, String key) throws Exception {
        var req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        if (key != null) {
            req.addHeader(ApiKeyFilter.HEADER, key);
        }
        var res = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> ((MockHttpServletResponse) rs).setStatus(200);
        filter.doFilter(req, res, chain);
        return res.getStatus();
    }

    @Test
    void missingKeyIs401() throws Exception {
        assertThat(call("/api/employee/ask", null)).isEqualTo(401);
    }

    @Test
    void wrongKeyIs401() throws Exception {
        assertThat(call("/api/employee/ask", "nope")).isEqualTo(401);
    }

    @Test
    void correctKeyPasses() throws Exception {
        assertThat(call("/api/employee/ask", "secret")).isEqualTo(200);
    }

    @Test
    void nonApiPathsAreNotFiltered() throws Exception {
        assertThat(call("/actuator/health", null)).isEqualTo(200);
    }
}
