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
        urlShortenerService.createShortLink(longUrl);
        urlShortenerService.createShortLink(longUrl);
        urlShortenerService.createShortLink(longUrl);

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
    }
}