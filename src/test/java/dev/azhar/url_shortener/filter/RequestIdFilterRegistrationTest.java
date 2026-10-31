package dev.azhar.url_shortener.filter;

import dev.azhar.url_shortener.controller.UrlShortenerController;
import dev.azhar.url_shortener.exception.UrlNotFoundException;
import dev.azhar.url_shortener.model.LongLink;
import dev.azhar.url_shortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.concurrent.atomic.AtomicReference;

import static dev.azhar.url_shortener.common.Constants.REQUEST_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Proves Spring runs RequestIdFilter on a real request. RequestIdFilterChainTest covers the
// filter's own behaviour; this only checks it is wired in front of the controller.
@ActiveProfiles("test")
@WebMvcTest(UrlShortenerController.class)
class RequestIdFilterRegistrationTest {

    private static final String ALIAS = "abc1234";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @Test
    void given_request_when_handled_then_requestId_is_present_during_and_cleared_after() throws Exception {
        // given
        AtomicReference<String> requestIdDuringRequest = new AtomicReference<>();

        given(urlShortenerService.getLongUrl(ALIAS)).willAnswer(invocation -> {
            requestIdDuringRequest.set(MDC.get(REQUEST_ID_KEY));
            return LongLink.of( "https://example.com", anyString());
        });

        // when
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", ALIAS))
                .andExpect(status().isFound());

        // then
        assertThat(requestIdDuringRequest.get()).isNotBlank();
        assertThat(MDC.get(REQUEST_ID_KEY)).isNull();
    }

    @Test
    void given_handled_exception_when_request_then_requestId_is_present_during_and_cleared_after() throws Exception {
        // given
        AtomicReference<String> requestIdDuringRequest = new AtomicReference<>();

        given(urlShortenerService.getLongUrl(ALIAS)).willAnswer(invocation -> {
            requestIdDuringRequest.set(MDC.get(REQUEST_ID_KEY));
            throw new UrlNotFoundException(ALIAS);
        });

        // when
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", ALIAS))
                .andExpect(status().isNotFound());

        // then
        assertThat(requestIdDuringRequest.get()).isNotBlank();
        assertThat(MDC.get(REQUEST_ID_KEY)).isNull();
    }

    @Test
    void given_unhandled_exception_when_request_then_requestId_is_present_during_and_cleared_after() {
        // given
        AtomicReference<String> requestIdDuringRequest = new AtomicReference<>();

        given(urlShortenerService.getLongUrl(ALIAS)).willAnswer(invocation -> {
            requestIdDuringRequest.set(MDC.get(REQUEST_ID_KEY));
            throw new IllegalStateException("simulated failure");
        });

        // when / then
        assertThatThrownBy(() -> mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", ALIAS)))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(requestIdDuringRequest.get()).isNotBlank();
        assertThat(MDC.get(REQUEST_ID_KEY)).isNull();
    }
}
