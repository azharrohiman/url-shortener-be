package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.exception.UrlNotFoundException;
import dev.azhar.url_shortener.mapper.UrlAliasMapper;
import dev.azhar.url_shortener.model.ShortLink;
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
    public ShortLink createShortLink(String longUrl) {
        long nextId = urlAliasRepository.getNextId();

        UrlAlias urlAlias = new UrlAlias(nextId, longUrl, aliasGenerator.encode(nextId));

        entityManager.persist(urlAlias);

        // The entity stops here. Callers get an immutable record, so nothing above this layer can
        // hold — or accidentally mutate — a row that Hibernate is still managing.
        return UrlAliasMapper.toShortLink(urlAlias);
    }

    public String getLongUrl(String alias) {
        return urlAliasRepository.findByUrlAlias(alias)
                .map(UrlAlias::getLongUrl)
                .orElseThrow(() -> new UrlNotFoundException(alias));
    }
}
