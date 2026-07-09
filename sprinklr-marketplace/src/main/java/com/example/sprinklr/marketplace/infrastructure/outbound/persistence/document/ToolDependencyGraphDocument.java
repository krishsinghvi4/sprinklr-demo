package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import java.time.Instant;
import java.util.List;

/**
 * Embedded sub-document persisting an LLM-generated tool dependency graph inside
 * {@link McpConnectionDocument}. Kept on the connection so it is naturally per-user, per-server and
 * deleted together with the connection (multi-tenant isolation comes for free).
 *
 * @param edges            list of tool → direct prerequisite edges (avoids dotted map keys in MongoDB)
 * @param toolsFingerprint hash of the connection's tool-name set, used to detect a stale graph
 * @param generatedAt      when the graph was produced
 */
public record ToolDependencyGraphDocument(
        List<EdgeDocument> edges,
        String toolsFingerprint,
        Instant generatedAt
) {}
