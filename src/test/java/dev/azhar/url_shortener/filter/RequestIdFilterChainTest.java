package dev.azhar.url_shortener.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.azhar.url_shortener.common.Constants.REQUEST_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RequestIdFilterChainTest {

    private RequestIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setup() {
        filter = new RequestIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void given_doFilter_is_called_then_requestId_is_stored_during_the_request_and_cleared_after_filterChain_doFilter() throws ServletException, IOException {
        // given
        MDC.put("some-key", "some-value");

        AtomicReference<String> requestIdDuringRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            requestIdDuringRequest.set(MDC.get(REQUEST_ID_KEY));

            return null;
        }).when(filterChain).doFilter(request, response);

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(requestIdDuringRequest.get())
                .isNotBlank();
        assertThat(MDC.get(REQUEST_ID_KEY))
                .isNull();
        assertThat(MDC.getCopyOfContextMap())
                .isNotEmpty();
    }

    @Test
    void given_doFilter_is_called_twice_then_two_unique_requestIds_are_stored_in_MDC() throws ServletException, IOException {
        // given
        MDC.put("some-key", "some-value");

        List<String> requestIds = new ArrayList<>();

        doAnswer(invocation -> {
            requestIds.add(MDC.get(REQUEST_ID_KEY));

            return null;
        }).when(filterChain).doFilter(request, response);

        // when
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(requestIds)
                .hasSize(2)
                .doesNotContainNull()
                .doesNotHaveDuplicates();

        assertThat(MDC.getCopyOfContextMap())
                .isNotEmpty();
    }

    @Test
    void given_filterChain_doFilter_throws_exception_then_requestId_is_cleared() throws ServletException, IOException {
        // given
        MDC.put("some-key", "some-value");

        doThrow(ServletException.class)
                .when(filterChain).doFilter(request, response);

        // when / then
        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(REQUEST_ID_KEY)).isNull();

        assertThat(MDC.getCopyOfContextMap())
                .isNotEmpty();
    }
}
