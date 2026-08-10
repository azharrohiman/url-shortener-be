package dev.azhar.url_shortener.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasGeneratorTest {

    private static AliasGenerator aliasGenerator;

    @BeforeAll
    static void setUp() {
        aliasGenerator = new AliasGenerator();
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1, 2, 42, 1000, 999999, Long.MAX_VALUE
    })
    void given_long_ids_when_alias_is_generated_then_pattern_is_alphanumeric(long id) {
        // when
        String alias = aliasGenerator.encode(id);

        // then
        assertThat(alias).matches("[0-9a-zA-Z]+");
    }

    /**
     * alphanumeric only, never - or _, and changing this breaks the custom-alias plan (F4).
     */
    @Test
    void given_alphabet_then_pattern_should_be_alphanumeric_and_62_chars_long() {
        String alphabet = AliasGenerator.ALPHABET;

        // then
        assertThat(alphabet)
                .matches("[0-9a-zA-Z]+");

        assertThat(alphabet.chars().distinct().count())
                .isEqualTo(62);
    }

    @Test
    void given_alias_is_generated_for_unique_ids_then_alias_set_should_match_number_of_unique_ids() {
        // when
        Set<String> aliases = LongStream.rangeClosed(1, 1000)
                .mapToObj(aliasGenerator::encode)
                .collect(Collectors.toSet());

        // then
        assertThat(aliases).hasSize(1000);
    }

    @Test
    void given_id_then_encode_should_generate_the_same_alias() {
        // when
        String alias1 = aliasGenerator.encode(1L);
        String alias2 = aliasGenerator.encode(1L);

        // then
        assertThat(alias1).isEqualTo(alias2);
    }

    @Test
    void given_id_is_encoded_then_alias_should_be_at_least_7_chars_long() {
        // when
        String alias = aliasGenerator.encode(1L);

        // then
        assertThat(alias).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void given_id_less_than_1_then_encode_should_throw_an_exception() {
        assertThatThrownBy(() -> aliasGenerator.encode(0L))
                .hasMessage("Id must be greater or equal to 1, got " + 0L);
    }
}