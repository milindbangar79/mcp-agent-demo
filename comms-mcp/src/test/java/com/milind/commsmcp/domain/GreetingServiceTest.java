package com.milind.commsmcp.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    // ── generic greeting ────────────────────────────────────────────────────

    @Test
    void greet_nullName_returnsGenericHelloWorld() {
        assertThat(service.greet(null)).isEqualTo("Hello, World!");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void greet_blankOrNullName_returnsGenericGreeting(String name) {
        assertThat(service.greet(name)).isEqualTo("Hello, World!");
    }

    // ── personalised greeting ───────────────────────────────────────────────

    @Test
    void greet_validName_returnsPersonalisedGreeting() {
        assertThat(service.greet("Priya")).contains("Priya");
    }

    @Test
    void greet_validName_greetingStartsWithHello() {
        assertThat(service.greet("Milind")).startsWith("Hello,");
    }

    @Test
    void greet_validName_mentionsServerName() {
        assertThat(service.greet("Alice")).contains("comms-mcp");
    }

    @Test
    void greet_nameWithLeadingSpaces_trimmedInResponse() {
        String result = service.greet("  Bob");
        assertThat(result).contains("Bob");
        assertThat(result).doesNotContain("  Bob");
    }

    @Test
    void greet_nameWithTrailingSpaces_trimmedInResponse() {
        String result = service.greet("Charlie  ");
        assertThat(result).contains("Charlie");
        assertThat(result).doesNotContain("Charlie  ");
    }

    @Test
    void greet_nameWithBothLeadingAndTrailingSpaces_trimmedInResponse() {
        String result = service.greet("  Dave  ");
        assertThat(result).contains("Dave");
        assertThat(result).doesNotContain("  Dave  ");
    }

    // ── return type ─────────────────────────────────────────────────────────

    @Test
    void greet_alwaysReturnsNonNullString() {
        assertThat(service.greet(null)).isNotNull();
        assertThat(service.greet("")).isNotNull();
        assertThat(service.greet("Test")).isNotNull();
    }

    @Test
    void greet_alwaysReturnsNonEmptyString() {
        assertThat(service.greet(null)).isNotEmpty();
        assertThat(service.greet("")).isNotEmpty();
        assertThat(service.greet("Test")).isNotEmpty();
    }
}
