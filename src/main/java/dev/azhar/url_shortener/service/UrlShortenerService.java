package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.exception.AliasLengthLimitException;
import dev.azhar.url_shortener.exception.UrlNotFoundException;
import dev.azhar.url_shortener.mapper.UrlAliasMapper;
import dev.azhar.url_shortener.model.ShortLink;
import dev.azhar.url_shortener.repository.UrlAliasRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.azhar.url_shortener.common.Constants.ALIAS_LENGTH_LIMIT;

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
        if (alias.length() > ALIAS_LENGTH_LIMIT) {
            throw new AliasLengthLimitException(alias.length());
        }

        return urlAliasRepository.findByUrlAlias(alias)
                .map(UrlAlias::getLongUrl)
                .orElseThrow(() -> new UrlNotFoundException(alias));
    }
}
