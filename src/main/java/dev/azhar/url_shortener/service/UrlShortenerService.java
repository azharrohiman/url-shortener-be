package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.repository.UrlAliasRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final EntityManager entityManager;
    private final AliasGenerator aliasGenerator;
    private final UrlAliasRepository urlAliasRepository;

    @Transactional
    public UrlAlias createShortLink(String longUrl) {
        UrlAlias urlAlias = new UrlAlias();

        long nextId = urlAliasRepository.getNextId();

        urlAlias.setId(nextId);
        urlAlias.setLongUrl(longUrl);
        urlAlias.setUrlAlias(aliasGenerator.encode(nextId));

        entityManager.persist(urlAlias);

        return urlAlias;
    }
}
