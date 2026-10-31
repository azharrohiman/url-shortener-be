package dev.azhar.url_shortener.model;

public record LongLink(String url, String alias) {

    public static LongLink of(String url, String alias) {
        return new LongLink(url, alias);
    }
}
