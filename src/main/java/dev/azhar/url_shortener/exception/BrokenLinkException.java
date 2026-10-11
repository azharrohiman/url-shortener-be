package dev.azhar.url_shortener.exception;

import lombok.Getter;

@Getter
public class BrokenLinkException extends RuntimeException {
    private final String alias;

    public BrokenLinkException(IllegalArgumentException ex, String alias) {
        super(ex);
        this.alias = alias;
    }
}
