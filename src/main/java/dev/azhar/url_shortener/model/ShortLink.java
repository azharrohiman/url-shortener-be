package dev.azhar.url_shortener.model;

/**
 * A shortened link as the rest of the application sees it — immutable, and free of any
 * persistence concern.
 *
 * <p>This is what the service layer returns instead of the {@code UrlAlias} entity. An entity
 * returned from a {@code @Transactional} method arrives at its caller detached, mutable, and
 * still carrying a JPA lifecycle; nothing above the service has any business with that. Keeping
 * the two types apart also means the table can gain columns without widening what the web layer
 * can see.
 *
 * <p>Deliberately narrower than the entity: no {@code id} (a persistence detail) and no
 * {@code createdAt} (not part of the API contract in {@code docs/DESIGN.md}). Add either when a
 * caller genuinely needs it.
 */
public record ShortLink(String alias, String longUrl) {
}
