package dev.azhar.url_shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Boots only the probe, so Spring Boot applies the logging config without needing a database.
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = AliasInstrumentation.class)
class AliasInstrumentationTest {

    @Autowired
    AliasInstrumentation aliasInstrumentation;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void given_alias_when_aliasCreated_then_info_line_carries_alias_as_its_own_field(CapturedOutput output) {
        // given
        String alias = "alias-" + UUID.randomUUID();

        // when
        aliasInstrumentation.aliasCreated(new AliasCreatedEvent(alias));

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(alias))
                .findFirst()
                .orElseThrow();

        JsonNode json = jsonMapper.readTree(line);

        assertThat(json.at("/message").asString()).isEqualTo("Alias created");
        assertThat(json.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(json.at("/log/logger").asString()).isEqualTo(AliasInstrumentation.class.getName());
        assertThat(json.at("/alias").asString()).isEqualTo(alias);
    }

    @Test
    void given_alias_when_aliasLengthExceeded_then_info_line_carries_alias_as_its_own_field(CapturedOutput output) {
        // given
        String alias = "alias-" + UUID.randomUUID();

        // when
        aliasInstrumentation.aliasLengthExceeded(alias);

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(alias))
                .findFirst()
                .orElseThrow();

        JsonNode json = jsonMapper.readTree(line);

        assertThat(json.at("/message").asString()).isEqualTo("Alias length exceeded");
        assertThat(json.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(json.at("/log/logger").asString()).isEqualTo(AliasInstrumentation.class.getName());
        assertThat(json.at("/alias").asString()).isEqualTo(alias);
    }

    @Test
    void given_alias_when_aliasNotFound_then_info_line_carries_alias_as_its_own_field(CapturedOutput output) {
        // given
        String alias = "alias-" + UUID.randomUUID();

        // when
        aliasInstrumentation.aliasNotFound(alias);

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(alias))
                .findFirst()
                .orElseThrow();

        JsonNode json = jsonMapper.readTree(line);

        assertThat(json.at("/message").asString()).isEqualTo("Alias not found");
        assertThat(json.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(json.at("/log/logger").asString()).isEqualTo(AliasInstrumentation.class.getName());
        assertThat(json.at("/alias").asString()).isEqualTo(alias);
    }

    @Test
    void given_alias_when_linkBroken_then_error_line_carries_alias_as_its_own_field(CapturedOutput output) {
        // given
        String alias = "alias-" + UUID.randomUUID();

        // when
        aliasInstrumentation.linkBroken(alias);

        // then
        String line = output.getOut().lines()
                .filter(l -> l.contains(alias))
                .findFirst()
                .orElseThrow();

        JsonNode json = jsonMapper.readTree(line);

        assertThat(json.at("/message").asString()).isEqualTo("Link broken");
        assertThat(json.at("/log/level").asString()).isEqualTo("ERROR");
        assertThat(json.at("/log/logger").asString()).isEqualTo(AliasInstrumentation.class.getName());
        assertThat(json.at("/alias").asString()).isEqualTo(alias);
    }
}
