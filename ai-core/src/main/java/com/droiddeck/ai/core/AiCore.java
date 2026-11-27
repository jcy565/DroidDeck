package com.droiddeck.ai.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bundled AI module contract. No Android, native runtime, networking or file I/O. */
public final class AiCore {
    private AiCore() {}

    private static final String VERSION = "0.1.0";
    public static String version() { return VERSION; }
    public static final int MIN_HOST_API = 1;
    public static final int MAX_HOST_API = 1;
    public static final int DRAFT_SCHEMA = 1;

    public enum Status { COMPATIBLE, HOST_API_MISMATCH, DRAFT_SCHEMA_MISMATCH }

    public static Status compatibility(int hostApi, int minDraftSchema, int maxDraftSchema) {
        if (hostApi < MIN_HOST_API || hostApi > MAX_HOST_API) return Status.HOST_API_MISMATCH;
        if (minDraftSchema > maxDraftSchema || DRAFT_SCHEMA < minDraftSchema || DRAFT_SCHEMA > maxDraftSchema) {
            return Status.DRAFT_SCHEMA_MISMATCH;
        }
        return Status.COMPATIBLE;
    }

    public enum ExchangeStatus { MATCH, SOURCE_CHANGED, DRAFT_CHANGED }

    /** Local-only snapshot. The target path must not be included in the outgoing prompt. */
    public static final class Exchange {
        private final String target;
        private final String source;
        private final Map<String, String> edits;
        private final String id;

        public Exchange(String target, String source, Map<String, String> edits) {
            this(target, source, edits, UUID.randomUUID().toString());
        }

        public Exchange(String target, String source, Map<String, String> edits, String restoredId) {
            if (!UUID.fromString(restoredId).toString().equals(restoredId)) throw new IllegalArgumentException("Invalid exchange ID");
            if (Objects.requireNonNull(target).trim().isEmpty()) throw new IllegalArgumentException("Empty target");
            this.target = target;
            this.source = Objects.requireNonNull(source);
            this.edits = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(edits)));
            this.id = restoredId;
        }

        public String id() { return id; }
        public ExchangeStatus check(String currentTarget, String currentSource, Map<String, String> currentEdits) {
            if (!target.equals(currentTarget) || !source.equals(currentSource)) return ExchangeStatus.SOURCE_CHANGED;
            if (!edits.equals(currentEdits)) return ExchangeStatus.DRAFT_CHANGED;
            return ExchangeStatus.MATCH;
        }
    }
}
