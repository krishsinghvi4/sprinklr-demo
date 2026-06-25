package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Embedded sub-document persisting an LLM-generated tool dependency graph inside
 * {@link McpConnectionDocument}. Kept on the connection so it is naturally per-user, per-server and
 * deleted together with the connection (multi-tenant isolation comes for free).
 *
 * @param edges            fully-qualified tool name -> direct prerequisite tool names
 * @param toolsFingerprint hash of the connection's tool-name set, used to detect a stale graph
 * @param generatedAt      when the graph was produced
 */
public record ToolDependencyGraphDocument(
        Map<String, List<String>> edges,
        String toolsFingerprint,
        Instant generatedAt
) {}
