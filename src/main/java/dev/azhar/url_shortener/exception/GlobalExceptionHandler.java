package dev.azhar.url_shortener.exception;

import java.util.HashMap;
import java.util.Map;

import dev.azhar.url_shortener.response.AliasLengthLimitResponseDto;
import dev.azhar.url_shortener.response.BrokenLinkResponseDto;
import dev.azhar.url_shortener.response.UrlNotFoundResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<UrlNotFoundResponseDto> handleUrlNotFound(UrlNotFoundException ex) {
        UrlNotFoundResponseDto response = UrlNotFoundResponseDto.builder()
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(BrokenLinkException.class)
    public ResponseEntity<BrokenLinkResponseDto> handleBrokenLink(BrokenLinkException ex) {
        log.error("Alias related to broken URL is: {}", ex.getAlias(), ex);

        BrokenLinkResponseDto response = BrokenLinkResponseDto.builder()
                .message("This link is broken on our side. Let the person who shared it know.")
                .build();

        return ResponseEntity.internalServerError()
                .body(response);
    }

    @ExceptionHandler(AliasLengthLimitException.class)
    public ResponseEntity<AliasLengthLimitResponseDto> buildAliasLengthLimitResponse(AliasLengthLimitException ex) {
        AliasLengthLimitResponseDto response = AliasLengthLimitResponseDto.builder()
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}
