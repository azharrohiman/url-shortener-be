package dev.azhar.url_shortener.repository;

import dev.azhar.url_shortener.entity.UrlAlias;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link UrlAlias}. {@code findByUrlAlias} is the redirect hot path
 * (build-order step 4) and is backed by the {@code UQ_URL_ALIAS} unique index.
 */
@Repository
public interface UrlAliasRepository extends JpaRepository<UrlAlias, Long> {

    Optional<UrlAlias> findByUrlAlias(String urlAlias);

    @Query("SELECT nextval('SEQ_URL_ALIAS')")
    long getNextId();
}
