package dev.azhar.url_shortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Maps the {@code TB_URL_ALIAS} table created by Liquibase changeset
 * {@code 001_create_url_alias_table}. One row per shortened URL.
 */
@Entity
@Table(name = "TB_URL_ALIAS")
@Getter
@Setter
@NoArgsConstructor
public class UrlAlias {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "LONG_URL", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "URL_ALIAS", nullable = false, unique = true, length = 64)
    private String urlAlias;

    // Owned by the database: the column has DEFAULT now(), so Hibernate excludes it from the
    // INSERT and reads the generated value back. updatable = false — a creation time never changes.
    @Generated(event = EventType.INSERT)
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UrlAlias(long id, String longUrl, String urlAlias) {
        this.id = id;
        this.longUrl = longUrl;
        this.urlAlias = urlAlias;
    }
}
