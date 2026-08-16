package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.TestcontainersConfiguration;
import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.repository.UrlAliasRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UrlShortenerServiceTest {

    @Autowired
    UrlShortenerService urlShortenerService;

    @Autowired
    UrlAliasRepository urlAliasRepository;

    @AfterEach
    void cleanUp() {
        urlAliasRepository.deleteAllInBatch();
    }

    @Test
    void given_long_url_when_createShortLink_then_row_is_created() {
        String longUrl = "https://example.com";

        urlShortenerService.createShortLink(longUrl);
        urlShortenerService.createShortLink(longUrl);
        urlShortenerService.createShortLink(longUrl);

        List<UrlAlias> allUrlAliases = urlAliasRepository.findAll();

        assertThat(allUrlAliases)
                .hasSize(3);

        assertThat(allUrlAliases)
                .extracting(UrlAlias::getUrlAlias).isNotNull();

        assertThat(allUrlAliases)
                .extracting(UrlAlias::getLongUrl).containsExactly(longUrl, longUrl, longUrl);
    }

    @Test
    void given_long_url_is_null_then_exception_is_thrown() {
        assertThatThrownBy(() -> urlShortenerService.createShortLink(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL cannot be null");
    }
}