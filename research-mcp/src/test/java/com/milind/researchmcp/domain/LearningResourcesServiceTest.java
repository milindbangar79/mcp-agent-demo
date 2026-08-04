package com.milind.researchmcp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningResourcesServiceTest {

    private final LearningResourcesService service = new LearningResourcesService();

    // ── structure ───────────────────────────────────────────────────────────

    @Test
    void findResources_returnsNonNull() {
        assertThat(service.findResources("simple interest")).isNotNull();
    }

    @Test
    void findResources_reflectsTopicInResult() {
        var result = service.findResources("simple interest");
        assertThat(result.topic()).isEqualTo("simple interest");
    }

    @Test
    void findResources_allThreeCategoriesPresent() {
        var result = service.findResources("simple interest");
        assertThat(result.calculators()).isNotNull();
        assertThat(result.articles()).isNotNull();
        assertThat(result.trainingContent()).isNotNull();
    }

    // ── calculators ─────────────────────────────────────────────────────────

    @Test
    void findResources_calculatorsNotEmpty() {
        assertThat(service.findResources("simple interest").calculators()).isNotEmpty();
    }

    @Test
    void findResources_calculatorsHaveThreeEntries() {
        assertThat(service.findResources("simple interest").calculators()).hasSize(3);
    }

    @Test
    void findResources_eachCalculatorHasNameUrlAndDescription() {
        service.findResources("simple interest").calculators().forEach(r -> {
            assertThat(r.name()).isNotBlank();
            assertThat(r.url()).isNotBlank();
            assertThat(r.description()).isNotBlank();
        });
    }

    @Test
    void findResources_calculatorUrlsStartWithHttps() {
        service.findResources("simple interest").calculators().forEach(r ->
                assertThat(r.url()).startsWith("https://"));
    }

    // ── articles ────────────────────────────────────────────────────────────

    @Test
    void findResources_articlesNotEmpty() {
        assertThat(service.findResources("simple interest").articles()).isNotEmpty();
    }

    @Test
    void findResources_articlesHaveThreeEntries() {
        assertThat(service.findResources("simple interest").articles()).hasSize(3);
    }

    @Test
    void findResources_eachArticleHasNameUrlAndDescription() {
        service.findResources("simple interest").articles().forEach(r -> {
            assertThat(r.name()).isNotBlank();
            assertThat(r.url()).isNotBlank();
            assertThat(r.description()).isNotBlank();
        });
    }

    @Test
    void findResources_articleUrlsStartWithHttps() {
        service.findResources("simple interest").articles().forEach(r ->
                assertThat(r.url()).startsWith("https://"));
    }

    // ── training content ────────────────────────────────────────────────────

    @Test
    void findResources_trainingContentNotEmpty() {
        assertThat(service.findResources("simple interest").trainingContent()).isNotEmpty();
    }

    @Test
    void findResources_trainingContentHasThreeEntries() {
        assertThat(service.findResources("simple interest").trainingContent()).hasSize(3);
    }

    @Test
    void findResources_eachTrainingResourceHasNameUrlAndDescription() {
        service.findResources("simple interest").trainingContent().forEach(r -> {
            assertThat(r.name()).isNotBlank();
            assertThat(r.url()).isNotBlank();
            assertThat(r.description()).isNotBlank();
        });
    }

    // ── immutability ────────────────────────────────────────────────────────

    @Test
    void findResources_returnsDifferentInstanceEachCall() {
        var first = service.findResources("simple interest");
        var second = service.findResources("simple interest");
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void findResources_topicPassedThroughVerbatim() {
        var topic = "compound interest";
        assertThat(service.findResources(topic).topic()).isEqualTo(topic);
    }
}
