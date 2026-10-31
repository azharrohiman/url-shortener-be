package dev.azhar.url_shortener.exception;

import java.util.List;

import dev.azhar.url_shortener.response.ErrorResponseDto;
import dev.azhar.url_shortener.response.ValidationErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ValidationErrorDto> validation = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ValidationErrorDto.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .build())
                .toList();

        return buildResponse(ErrorResponseDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .title("The payload is invalid")
                .detail("The payload has one or more validation errors, please fix them and try again.")
                .validation(validation)
                .build());
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUrlNotFound(UrlNotFoundException ex) {
        return buildResponse(ErrorResponseDto.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .title("URL not found")
                .detail(ex.getMessage())
                .build());
    }

    @ExceptionHandler(BrokenLinkException.class)
    public ResponseEntity<ErrorResponseDto> handleBrokenLink(BrokenLinkException ex) {
        return buildResponse(ErrorResponseDto.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .title("Broken link")
                .detail("This link is broken on our side. Let the person who shared it know.")
                .build());
    }

    @ExceptionHandler(AliasLengthLimitException.class)
    public ResponseEntity<ErrorResponseDto> handleAliasLengthLimit(AliasLengthLimitException ex) {
        return buildResponse(ErrorResponseDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .title("Alias too long")
                .detail(ex.getMessage())
                .build());
    }

    private ResponseEntity<ErrorResponseDto> buildResponse(ErrorResponseDto body) {
        return ResponseEntity.status(body.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
