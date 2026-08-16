package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.repository.UrlAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final AliasGenerator aliasGenerator;
    private final UrlAliasRepository urlAliasRepository;

    @Transactional
    public UrlAlias createShortLink(String longUrl) {
        if (isNull(longUrl)) throw new IllegalArgumentException("URL cannot be null");

        UrlAlias urlAlias = new UrlAlias();

        long nextId = urlAliasRepository.getNextId();

        urlAlias.setId(nextId);
        urlAlias.setLongUrl(longUrl);
        urlAlias.setUrlAlias(aliasGenerator.encode(nextId));

        return urlAliasRepository.save(urlAlias);
    }
}
