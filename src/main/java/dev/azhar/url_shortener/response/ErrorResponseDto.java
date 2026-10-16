package dev.azhar.url_shortener.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        int status,
        String title,
        String detail,
        List<ValidationErrorDto> validation
) {
}
