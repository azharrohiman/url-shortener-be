package dev.azhar.url_shortener.controller;

import dev.azhar.url_shortener.exception.AliasLengthLimitException;
import dev.azhar.url_shortener.exception.UrlNotFoundException;
import dev.azhar.url_shortener.model.ShortLink;
import dev.azhar.url_shortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static dev.azhar.url_shortener.common.Constants.ALIAS_LENGTH_LIMIT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(UrlShortenerController.class)
class UrlShortenerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService urlShortenerService;
    
    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "ftp://example",
            "file:some-file"
    })
    void given_validUrl_when_create_then_isCreated(String url) throws Exception {
        // given
        String validUrlDto = """
                {
                    "url": "%s"
                }
                """.formatted(url);

        String alias = "aB3kZ9q";

        given(urlShortenerService.createShortLink(url))
                .willReturn(new ShortLink(alias, url));

        // then
        String shortLink = "http://localhost-test/%s";

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUrlDto))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.longUrl").value(url))
                .andExpect(jsonPath("$.shortUrl").value(String.format(shortLink, alias)))
                .andExpect(jsonPath("$.alias").value(alias))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(redirectedUrl(String.format(shortLink, alias)));
    }

    @Test
    void given_blankUrl_when_create_then_400_Is_returned() throws Exception {
        // given
        String blankUrlDto = """
                {
                    "url": ""
                }
                """;

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankUrlDto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.url").exists());

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void given_invalidUrl_when_create_then_400_Is_returned() throws Exception {
        // given
        String invalidUrlDto = """
                {
                    "url": "an-invalid-url"
                }
                """;

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUrlDto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.url").exists());

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void given_urlSize_is_greater_than_2048_when_create_then_400_Is_returned() throws Exception {
        // given
        String longUrl = "https://example.com/" + "a".repeat(2048);

        String longUrlDto = """
                {
                    "url": "%s"
                }
                """.formatted(longUrl);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longUrlDto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.url").value("URL must not exceed 2048 characters"));

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void given_empty_request_body_when_create_then_400_Is_returned() throws Exception {
        // given
        String emptyRequest = """
                {}
                """;

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyRequest))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.url").exists());

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void given_null_url_in_request_when_create_then_400_Is_returned() throws Exception {
        // given
        String nullUrlRequest = """
                {
                    "url": null
                }
                """;

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullUrlRequest))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.url").exists());

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void given_alias_in_redirect_then_response_contains_url_and_found_status() throws Exception {
        // given
        String alias = "some-alias";

        given(urlShortenerService.getLongUrl(alias))
                .willReturn("https://example.com");

        // then
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", alias))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://example.com"));
    }

    @Test
    void given_unknown_alias_when_redirect_then_404_Is_returned() throws Exception {
        // given
        String unknownAlias = "unknown-alias";

        given(urlShortenerService.getLongUrl(unknownAlias))
                .willThrow(new UrlNotFoundException(unknownAlias));

        // then
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", unknownAlias))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(String.format("No URL found with alias: %s", unknownAlias))
                );
    }

    @Test
    void given_long_url_is_malformed_when_redirect_then_internal_server_error() throws Exception {
        // given
        given(urlShortenerService.getLongUrl("some-alias"))
                .willReturn("malformed url");

        // then
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", "some-alias"))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("This link is broken on our side. Let the person who shared it know."));
    }

    @Test
    void given_alias_exceeds_length_limit_then_bad_request() throws Exception {
        // given
        String longAlias = "a".repeat(ALIAS_LENGTH_LIMIT + 1);

        given(urlShortenerService.getLongUrl(longAlias))
                .willThrow(new AliasLengthLimitException(longAlias.length()));

        // then
        mockMvc.perform(MockMvcRequestBuilders.get("/{alias}", longAlias))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(String.format("Alias length %d exceeds limit of %d.", longAlias.length(), ALIAS_LENGTH_LIMIT))
                );
    }
}