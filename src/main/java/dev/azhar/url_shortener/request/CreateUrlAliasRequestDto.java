package dev.azhar.url_shortener.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateUrlAliasRequestDto(
        @NotBlank(message = "URL must not be blank.")
        @Size(max = 2048, message = "URL must not exceed 2048 characters.")
        @URL(message = "URL is not valid.")
        String url
) {
}
