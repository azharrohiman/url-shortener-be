package dev.azhar.url_shortener.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String alias) {
        super(String.format("No URL found with alias: %s", alias));
    }
}
