package dev.azhar.url_shortener.exception;

import static dev.azhar.url_shortener.common.Constants.ALIAS_LENGTH_LIMIT;

public class AliasLengthLimitException extends RuntimeException {
    public AliasLengthLimitException(int length) {
        super(String.format("Alias length %d exceeds limit of %d.", length, ALIAS_LENGTH_LIMIT));
    }
}
