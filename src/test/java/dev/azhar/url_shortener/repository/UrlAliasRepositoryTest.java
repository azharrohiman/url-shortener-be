package dev.azhar.url_shortener.repository;

import dev.azhar.url_shortener.entity.UrlAlias;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import dev.azhar.url_shortener.TestcontainersConfiguration;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UrlAliasRepositoryTest {

    @Autowired
    private UrlAliasRepository urlAliasRepository;

    @AfterEach
    void cleanUp() {
        urlAliasRepository.deleteAllInBatch();
    }

    @Test
    void given_findAll_then_should_return_all_url_aliases() {
        UrlAlias urlAlias = new UrlAlias("example.com", "abc");
        UrlAlias urlAlias2 = new UrlAlias("example2.com", "def");

        assertNull(urlAlias.getId());
        assertNull(urlAlias2.getId());

        UrlAlias savedUrlAlias1 = urlAliasRepository.saveAndFlush(urlAlias);
        UrlAlias savedUrlAlias2 = urlAliasRepository.saveAndFlush(urlAlias2);

        List<UrlAlias> allUrlAliases = urlAliasRepository.findAll();

        assertThat(allUrlAliases)
                .hasSize(1);

        assertThat(savedUrlAlias2.getId() - savedUrlAlias1.getId()).isEqualTo(1L);
    }

    @Test
    void given_findByAlias_then_should_return_url_alias() {
        UrlAlias urlAlias1 = new UrlAlias("example.com", "abc");
        UrlAlias urlAlias2 = new UrlAlias("example2.com", "def");

        urlAliasRepository.saveAndFlush(urlAlias1);
        urlAliasRepository.saveAndFlush(urlAlias2);

        Optional<UrlAlias> urlAlias = urlAliasRepository.findByUrlAlias("abc");

        assertTrue(urlAlias.isPresent());
        assertNotNull(urlAlias.get().getId());
        assertEquals(urlAlias1.getUrlAlias(), urlAlias.get().getUrlAlias());
        assertEquals(urlAlias1.getLongUrl(), urlAlias.get().getLongUrl());
        assertThat(urlAlias.get().getCreatedAt())
                .isCloseTo(OffsetDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void given_long_url_is_not_unique_then_same_long_urls_can_be_saved() {
        UrlAlias urlAlias1 = new UrlAlias("example.com", "abc");
        UrlAlias urlAlias2 = new UrlAlias("example.com", "def");

        urlAliasRepository.saveAndFlush(urlAlias1);
        urlAliasRepository.saveAndFlush(urlAlias2);

        List<UrlAlias> allUrlAliases = urlAliasRepository.findAll();

        assertThat(allUrlAliases)
                .hasSize(2);
    }

    @Test
    void given_findByUrlAlias_does_not_exist_then_optional_url_alias_is_empty() {
        Optional<UrlAlias> urlAlias = urlAliasRepository.findByUrlAlias("abc");

        assertTrue(urlAlias.isEmpty());
    }

    @Test
    void given_null_long_url_is_saved_then_exception_is_thrown() {
        UrlAlias urlAlias = new UrlAlias(null, "abc");

        assertThrows(DataIntegrityViolationException.class,
                () -> urlAliasRepository.saveAndFlush(urlAlias)
        );
    }

    @Test
    void given_null_url_alias_is_saved_then_exception_is_thrown() {
        UrlAlias urlAlias = new UrlAlias("example.com", null);

        assertThrows(DataIntegrityViolationException.class,
                () -> urlAliasRepository.saveAndFlush(urlAlias)
        );
    }

    @Test
    void given_duplicate_url_alias_is_saved_again_then_exception_is_thrown() {
        UrlAlias urlAlias = new UrlAlias("example.com", "abc");

        urlAliasRepository.saveAndFlush(urlAlias);

        UrlAlias duplicateUrlAlias = new UrlAlias("a-different-example.com", "abc");

        assertThrows(DataIntegrityViolationException.class,
                () -> urlAliasRepository.saveAndFlush(duplicateUrlAlias)
        );
    }
}