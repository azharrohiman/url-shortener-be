package dev.azhar.url_shortener.controller;

import dev.azhar.url_shortener.config.AppProperties;
import dev.azhar.url_shortener.exception.BrokenLinkException;
import dev.azhar.url_shortener.model.ShortLink;
import dev.azhar.url_shortener.request.CreateUrlAliasRequestDto;
import dev.azhar.url_shortener.response.CreateUrlAliasResponseDto;
import dev.azhar.url_shortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;
    private final AppProperties appProperties;

    @GetMapping("/{alias}")
    public ResponseEntity<Void> redirect(@PathVariable String alias) {
        String url = urlShortenerService.getLongUrl(alias);

        try {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(url))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new BrokenLinkException(e, url);
        }
    }

    @PostMapping("/api/v1/links")
    public ResponseEntity<CreateUrlAliasResponseDto> shortenUrl(@Valid @RequestBody CreateUrlAliasRequestDto createUrlAliasRequestDto) {

        ShortLink shortLink = urlShortenerService.createShortLink(createUrlAliasRequestDto.url());

        URI shortUrl = UriComponentsBuilder
                .fromUriString(appProperties.baseUrl())
                .path("/{alias}")
                .buildAndExpand(shortLink.alias())
                .toUri();

        return ResponseEntity.created(shortUrl)
                .body(CreateUrlAliasResponseDto.builder()
                        .longUrl(shortLink.longUrl())
                        .shortUrl(shortUrl.toString())
                        .alias(shortLink.alias())
                .build());
    }
}
