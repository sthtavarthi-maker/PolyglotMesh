package com.polyglotmesh.dto;

/**
 * One language and whether this backend can execute it right now.
 *
 * @param id          API identifier, e.g. {@code javascript}
 * @param displayName human-readable name, e.g. {@code JavaScript}
 * @param available   true only when a real executor and its runtime/toolchain exist
 */
public record LanguageStatus(String id, String displayName, boolean available) {
}
