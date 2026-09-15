package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.TestcontainersConfiguration;
import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.exception.UrlNotFoundException;
import dev.azhar.url_shortener.model.ShortLink;
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

    @Autowired
    AliasGenerator aliasGenerator;

    @AfterEach
    void cleanUp() {
        urlAliasRepository.deleteAllInBatch();
    }

    @Test
    void given_long_url_when_createShortLink_then_row_is_created() {
        // given
        String longUrl = "https://example.com";

        // when
        List<ShortLink> createdShortLinks = List.of(
                urlShortenerService.createShortLink(longUrl),
                urlShortenerService.createShortLink(longUrl),
                urlShortenerService.createShortLink(longUrl));

        List<UrlAlias> allUrlAliases = urlAliasRepository.findAll();

        // then
        // Guards R7's arithmetic trap: row ids must come from nextval('SEQ_URL_ALIAS'), never from
        // `previousId + 1`. That mistake is invisible in the rows themselves — the ids look plausible
        // and every link still resolves — because the id and the alias are derived from the same wrong
        // number, so `alias == encode(id)` below still holds. The sequence is the only witness: after
        // three correct creates it has issued exactly the ids now in the table, so the next value it
        // hands out must be one past the highest stored id. Under `getNextId() + 1` the rows run one
        // ahead of the sequence and that gap closes to zero.
        //
        // Don't delete this as incidental. F4's unique-violation retry (DECISIONS.md R7, SCENARIO.md)
        // is where "just add one" becomes tempting, and this is its only regression test.
        //
        // Side effect: the getNextId() call below consumes a sequence value, so this test is not
        // idempotent with respect to SEQ_URL_ALIAS. Harmless — cleanup deletes the rows and the
        // sequence simply advances.
        long highestStoredId = allUrlAliases.stream()
                .mapToLong(UrlAlias::getId)
                .max()
                .orElseThrow(() -> new AssertionError(
                        "Expected createShortLink to have persisted rows, but the table was empty — "
                                + "there is no id to check SEQ_URL_ALIAS against."));

        assertThat(urlAliasRepository.getNextId())
                .as("SEQ_URL_ALIAS should sit one past the highest stored id (%d). If it does not, "
                        + "the service is deriving row ids by arithmetic instead of taking each one "
                        + "from nextval — see R7.", highestStoredId)
                .isEqualTo(highestStoredId + 1);

        assertThat(allUrlAliases)
                .hasSize(3)
                .allSatisfy(row -> assertThat(row.getUrlAlias()).isEqualTo(aliasGenerator.encode(row.getId())));

        assertThat(allUrlAliases)
                .extracting(UrlAlias::getUrlAlias)
                .doesNotHaveDuplicates();

        assertThat(allUrlAliases)
                .extracting(UrlAlias::getLongUrl)
                .containsOnly(longUrl);

        // The service hands callers a ShortLink, never the entity: nothing above the service layer
        // can hold a mutable, JPA-managed row. What it returns must be exactly what it persisted.
        assertThat(createdShortLinks)
                .extracting(ShortLink::longUrl)
                .containsOnly(longUrl);

        assertThat(createdShortLinks)
                .extracting(ShortLink::alias)
                .containsExactlyInAnyOrderElementsOf(
                        allUrlAliases.stream().map(UrlAlias::getUrlAlias).toList());
    }

    @Test
    void given_alias_when_getLongUrl_then_long_url_is_returned() {
        // given
        UrlAlias urlAlias1 = new UrlAlias(1, "example.com", "abc");
        UrlAlias urlAlias2 = new UrlAlias(2, "example2.com", "def");

        urlAliasRepository.save(urlAlias1);
        urlAliasRepository.save(urlAlias2);

        // when
        String actual = urlShortenerService.getLongUrl("def");

        // then
        assertThat(actual)
                .isEqualTo("example2.com");
    }

    @Test
    void given_alias_is_uppercase_when_getLongUrl_in_lowercase_then_correct_throw_UrlNotFoundException() {
        // given
        UrlAlias urlAlias1 = new UrlAlias(1, "uppercase.com", "aBc");

        urlAliasRepository.save(urlAlias1);

        String lowercaseAlias = "abc";

        // then
        assertThatThrownBy(() -> urlShortenerService.getLongUrl(lowercaseAlias))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessage("No URL found with alias: " + lowercaseAlias);
    }

    @Test
    void given_alias_for_non_existing_url_when_getLongUrl_then_throw_UrlNotFoundException() {
        // given
        String alias = "non-existing-alias";

        // then
        assertThatThrownBy(() -> urlShortenerService.getLongUrl(alias))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessage("No URL found with alias: " + alias);
    }
}