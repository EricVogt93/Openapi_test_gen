package io.oatg.spec.model;

/** An operation the tool cannot handle yet — always surfaced, never silently dropped. */
public record SkippedOperation(String method, String pathTemplate, String reason) {
}
