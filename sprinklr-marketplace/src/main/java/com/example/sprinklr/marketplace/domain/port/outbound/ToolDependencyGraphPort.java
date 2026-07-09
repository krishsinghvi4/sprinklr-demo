package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;

import java.util.List;

/**
 * Builds a per-server tool dependency graph from a server's discovered tools (names, descriptions, and
 * parameter semantics). Implemented by an LLM-backed adapter and called once at MCP connect time.
 * <p>
 * Implementations must never throw for an unparseable/invalid LLM response: they return a graph with
 * {@link com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus#FAILED} so connect still
 * succeeds and chat degrades gracefully to router-only selection.
 */
public interface ToolDependencyGraphPort {

    /**
     * @param serverIdPrefix the connection's prefix (e.g. {@code "jira"}); scopes every edge
     * @param tools          the fully-qualified tools discovered for that one server
     * @return a graph (status {@code READY} on success, {@code FAILED} otherwise)
     */
    ToolDependencyGraph generate(String serverIdPrefix, List<McpTool> tools);

    /**
     * Returns an empty {@code READY} graph without calling the LLM (e.g. trivial tool count or fingerprinting
     * for catalog {@code staticDependencyGraph}).
     */
    ToolDependencyGraph emptyReadyGraph(String serverIdPrefix, List<McpTool> tools);
}
