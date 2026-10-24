package dev.azhar.url_shortener;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class StructuredLoggingTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void given_default_config_when_log_then_console_line_is_ecs_json(CapturedOutput output) {
        // given
        // A unique message, so the line can be picked out of everything else the context has
        // logged, and so a line left over from another test can't be mistaken for this one.
        String message = "structured-logging-" + UUID.randomUUID();

        // when
        log.info(message);

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(message))
                .findFirst()
                .orElseThrow();

        // Fails here, not on a field assertion, if the line is plain text.
        JsonNode json = jsonMapper.readTree(line);

        // ECS field names are nested objects in the JSON: "log.level" is {"log": {"level": ...}}.
        assertThat(json.at("/message").asString()).isEqualTo(message);
        assertThat(json.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(json.at("/log/logger").asString()).isEqualTo(StructuredLoggingTest.class.getName());
        assertThat(json.at("/service/name").asString()).isEqualTo("url-shortener");
        assertThat(json.at("/@timestamp").asString()).isNotBlank();
        assertThat(json.at("/ecs/version").asString()).isNotBlank();
    }

    @Test
    void given_local_profile_when_resolve_console_format_then_blank_so_plain_text() {
        // given
        // Resolves the config files as Spring Boot would for the "local" profile, without starting
        // an application. Starting one would reconfigure logging for the whole JVM, and the cached
        // contexts every other test class reuses would carry on logging plain text.
        StandardEnvironment environment = new StandardEnvironment();

        // when
        ConfigDataEnvironmentPostProcessor.applyTo(
                environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), "local");

        // then
        // Spring Boot uses the JSON encoder only when this property has a length, so an empty
        // value means plain text. Null would mean neither file sets it, and fails this assertion.
        assertThat(environment.getProperty("logging.structured.format.console")).isEmpty();
    }
}
