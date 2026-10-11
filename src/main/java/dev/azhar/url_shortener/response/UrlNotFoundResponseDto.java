package dev.azhar.url_shortener.response;

import lombok.Builder;

@Builder
public record UrlNotFoundResponseDto(
        String message
) {
}
