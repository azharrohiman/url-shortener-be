package dev.azhar.url_shortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UrlShortenerApplicationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void liquibaseCreatesUrlAliasTable() {
		// Ask the catalog whether the table exists, rather than counting its rows: the Spring
		// context (and therefore the Postgres container) is shared across test classes, so a
		// row count would couple this test to every other test's cleanup.
		// Note: Postgres folds unquoted identifiers to lower case, so the DDL's TB_URL_ALIAS
		// is stored as 'tb_url_alias'.
		Long tableCount = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name = 'tb_url_alias'
				""", Long.class);

		assertThat(tableCount).isOne();
	}

}
