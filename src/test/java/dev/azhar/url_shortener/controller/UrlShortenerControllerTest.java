package dev.azhar.url_shortener.controller;

import dev.azhar.url_shortener.config.AppProperties;
import dev.azhar.url_shortener.model.ShortLink;
import dev.azhar.url_shortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.BDDMockito.given;
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
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
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
    }
}