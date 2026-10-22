package dev.azhar.url_shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthCheckTest {

    @LocalServerPort
    int port;

    @Autowired
    HealthEndpoint healthEndpoint;

    RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void given_app_and_database_up_when_get_health_then_200_with_status_only() {
        // when / then
        // Strict comparison: the body must be exactly this. Anything more — component names,
        // database product, disk space — is internal detail an anonymous caller has no use for.
        // "groups" names the liveness and readiness checks Spring Boot adds by default; it lists
        // only their names, not their contents.
        restTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        {
                            "status": "UP",
                            "groups": ["liveness", "readiness"]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void given_database_up_when_check_db_component_then_up() {
        // when
        // The HTTP response hides which checks ran, so ask the endpoint directly. This is what
        // makes "UP" mean "the app can reach Postgres", not just "the JVM is running": if the
        // database check were missing, a dead database would still report UP.
        HealthDescriptor db = healthEndpoint.healthForPath("db");

        // then
        assertThat(db)
                .isNotNull();

        assertThat(db.getStatus())
                .isEqualTo(Status.UP);
    }

    @Test
    void given_actuator_discovery_page_when_get_actuator_then_falls_through_to_redirect_404() {
        // when / then
        // "/actuator" is one path segment, so it sits in the redirect namespace (D5). If the
        // discovery page were left on, it would claim that path ahead of "/{alias}". With it off,
        // the request is treated as an alias lookup like any other, and no such alias exists.
        restTestClient.get()
                .uri("/actuator")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json");
    }
}
