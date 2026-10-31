package dev.azhar.url_shortener.exception;

import lombok.Getter;

@Getter
public class BrokenLinkException extends RuntimeException {
    private final String alias;

    public BrokenLinkException(String alias) {
        this.alias = alias;
    }
}
