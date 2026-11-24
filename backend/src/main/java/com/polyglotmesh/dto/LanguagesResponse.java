package com.polyglotmesh.dto;

import java.util.List;

/**
 * Read-only capability response for {@code GET /api/languages}.
 *
 * <p>Every product language is listed, including those that answer 501, so a
 * client can render the full editor list and disable what this deployment
 * cannot run.</p>
 */
public record LanguagesResponse(List<LanguageStatus> languages) {

    public LanguagesResponse {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
