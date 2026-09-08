package dev.azhar.url_shortener.mapper;

import dev.azhar.url_shortener.entity.UrlAlias;
import dev.azhar.url_shortener.model.ShortLink;

/**
 * Translates the {@code TB_URL_ALIAS} entity into the domain type the service layer hands out.
 *
 * <p>The mapping lives here rather than as a factory method on either type: {@link ShortLink}
 * knowing about the entity would put a JPA dependency in the domain, and the entity knowing about
 * {@link ShortLink} would point the dependency the other way. A separate mapper leaves both ends
 * ignorant of each other.
 *
 * <p>Stateless, so it is a static utility rather than a Spring bean, and two fields do not justify
 * pulling in a mapping framework.
 */
public final class UrlAliasMapper {

    private UrlAliasMapper() {
    }

    public static ShortLink toShortLink(UrlAlias urlAlias) {
        return new ShortLink(urlAlias.getUrlAlias(), urlAlias.getLongUrl());
    }
}
