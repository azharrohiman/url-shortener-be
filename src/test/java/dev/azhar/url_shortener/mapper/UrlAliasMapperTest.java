package dev.azhar.url_shortener.mapper;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.model.ShortLink;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlAliasMapperTest {

    @Test
    void given_entity_when_toShortLink_then_alias_and_longUrl_are_carried_across() {
        // given
        UrlAlias urlAlias = new UrlAlias(42L, "https://example.com/some/very/long/path", "aB3kZ9q");

        // when
        ShortLink shortLink = UrlAliasMapper.toShortLink(urlAlias);

        // then
        assertThat(shortLink.alias()).isEqualTo("aB3kZ9q");
        assertThat(shortLink.longUrl()).isEqualTo("https://example.com/some/very/long/path");
    }
}
