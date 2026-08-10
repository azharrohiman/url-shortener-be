package dev.azhar.url_shortener.service;

import org.springframework.stereotype.Component;
import org.sqids.Sqids;

import java.util.List;

@Component
public class AliasGenerator {

    // alphanumeric only, never - or _, and changing this breaks the custom-alias plan (F4).
    static final String ALPHABET = "KvRVUYoGFXSchetfawqbPH2D6NM80Z35C7BzpTiW9jOmnkrQl4IAs1gEdyJuxL";
    private static final int MIN_LENGTH = 7;

    private final Sqids sqids = Sqids.builder()
            .alphabet(ALPHABET)
            .minLength(MIN_LENGTH)
            .build();

    public String encode(long id) {
        if (id < 1) throw new IllegalArgumentException(String.format("Id must be greater or equal to 1, got %d", id));

        return sqids.encode(List.of(id));
    }
}
