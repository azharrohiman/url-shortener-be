package dev.azhar.url_shortener.response;

import lombok.Builder;

@Builder
public record CreateUrlAliasResponseDto(
        String alias,
        String shortUrl,
        String longUrl
) {
}
