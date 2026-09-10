package dev.azhar.url_shortener.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateUrlAliasRequestDto(
        @NotBlank
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        @URL
        String url
) {
}
