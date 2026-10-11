package dev.azhar.url_shortener;

import dev.azhar.url_shortener.config.AppProperties;
import dev.azhar.url_shortener.repository.UrlAliasRepository;
import dev.azhar.url_shortener.response.CreateUrlAliasResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.URI;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core user journey, end to end: a real HTTP server, the real service, and a real Postgres.
 * Nothing is mocked, so this is the one test that proves the alias create stores is the alias
 * redirect finds — the slice tests can only prove each half on its own.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateAndRedirectJourneyTest {

    @LocalServerPort
    int port;

    @Autowired
    AppProperties appProperties;

    @Autowired
    UrlAliasRepository urlAliasRepository;

    RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        // Never follow redirects: the 302 and its Location header are what's under test. A client
        // that followed them would try to fetch the long URL itself and hide the redirect from us.
        HttpClient noRedirects = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        restTestClient = RestTestClient.bindToServer(new JdkClientHttpRequestFactory(noRedirects))
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void cleanUp() {
        urlAliasRepository.deleteAllInBatch();
    }

    @Test
    void given_valid_url_when_create_and_follow_shortUrl_then_redirect_to_long_url() {
        // given
        String longUrl = "https://example.com/some/very/long/path?with=query";

        String createRequest = """
                {
                    "url": "%s"
                }
                """.formatted(longUrl);

        // when
        CreateUrlAliasResponseDto created = restTestClient.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CreateUrlAliasResponseDto.class)
                .returnResult()
                .getResponseBody();

        // then
        assertThat(created)
                .isNotNull();

        // shortUrl is built from app.base-url — the public host — not from this server's random
        // port, so it can't be requested as-is. Assert the host is the configured one, then follow
        // its path against the running server. Taking the path from the response, rather than
        // rebuilding "/{alias}" here, is the point: if the URL handed out and the route served
        // ever drift apart, this test fails.
        assertThat(created.shortUrl())
                .startsWith(appProperties.baseUrl());

        String shortUrlPath = URI.create(created.shortUrl()).getPath();

        restTestClient.get()
                .uri(shortUrlPath)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().location(longUrl);
    }
}
