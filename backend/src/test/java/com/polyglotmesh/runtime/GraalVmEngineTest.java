package com.polyglotmesh.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.Language;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The engine reports what is actually installed, not what the product plans to
 * support. Day 11 bundles GraalJS and GraalPy.
 */
class GraalVmEngineTest {

    private static GraalVmEngine engine;

    @BeforeAll
    static void createEngine() {
        engine = new GraalVmEngine(properties(true));
    }

    @AfterAll
    static void closeEngine() {
        engine.close();
    }

    @Test
    void reportsBundledLanguages() {
        assertThat(engine.isEnabled()).isTrue();
        assertThat(engine.isAvailable()).isTrue();
        assertThat(engine.availableLanguageIds()).contains("js", "python");
        assertThat(engine.supports("js")).isTrue();
        assertThat(engine.supports("python")).isTrue();
    }

    @Test
    void reportsLanguagesWithoutArtifactsAsUnavailable() {
        assertThat(engine.supports("ruby")).isFalse();
        assertThat(engine.supports("cobol")).isFalse();
        assertThat(engine.supports(null)).isFalse();
    }

    @Test
    void sharesOneEngineInstance() {
        assertThat(engine.engine()).isSameAs(engine.engine());
    }

    @Test
    void mapsOnlyEmbeddedLanguagesToPolyglotIds() {
        assertThat(GraalVmEngine.polyglotLanguageId(Language.JAVASCRIPT)).contains("js");
        assertThat(GraalVmEngine.polyglotLanguageId(Language.PYTHON)).contains("python");
        assertThat(GraalVmEngine.polyglotLanguageId(Language.RUBY)).contains("ruby");
        assertThat(GraalVmEngine.polyglotLanguageId(Language.JAVA)).isEmpty();
        assertThat(GraalVmEngine.polyglotLanguageId(Language.C)).isEmpty();
        assertThat(GraalVmEngine.polyglotLanguageId(Language.GO)).isEmpty();
    }

    @Test
    void disabledEngineIsUnavailableAndCreatesNothing() {
        GraalVmEngine disabled = new GraalVmEngine(properties(false));
        try {
            assertThat(disabled.isEnabled()).isFalse();
            assertThat(disabled.isAvailable()).isFalse();
            assertThat(disabled.supports("js")).isFalse();
            assertThatThrownBy(disabled::engine).isInstanceOf(IllegalStateException.class);
        } finally {
            disabled.close();
        }
    }

    private static GraalVmProperties properties(boolean enabled) {
        return new GraalVmProperties(enabled, Duration.ofSeconds(3), 10_000, 2_000);
    }
}
