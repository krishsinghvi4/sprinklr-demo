package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolDependencyGraphPort;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Connect-time adapter that asks the LLM to infer a tool dependency graph for one MCP server.
 * <p>
 * Runs a single text-only LLM call (no function/tool schemas), parses + validates the result into a DAG,
 * retries once on invalid output, and never throws: on persistent failure it returns a graph with status
 * {@link DependencyGraphStatus#FAILED} so connect still succeeds and chat falls back to router-only selection.
 */
@Component
public class LlmToolDependencyGraphGenerator implements ToolDependencyGraphPort {

    private static final Logger log = LoggerFactory.getLogger(LlmToolDependencyGraphGenerator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 2;

    private final LlmService llmService;
    private final LlmSystemPromptLoader promptLoader;
    private final ToolDependencyGraphResponseParser responseParser;
    private final McpSkillPromptLoader skillPromptLoader;

    public LlmToolDependencyGraphGenerator(
            LlmService llmService,
            LlmSystemPromptLoader promptLoader,
            ToolDependencyGraphResponseParser responseParser,
            McpSkillPromptLoader skillPromptLoader
    ) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.responseParser = responseParser;
        this.skillPromptLoader = skillPromptLoader;
    }

    @Override
    public ToolDependencyGraph generate(String serverIdPrefix, List<McpTool> tools) {
        //Computes a SHA-256 hash of all tool names (sorted). This fingerprint is stored with the graph so that later,
        // the system can detect if the tool list has changed and the graph is stale.
        String fingerprint = fingerprint(tools);

        // Nothing to relate when a server has 0 or 1 tool — return an empty READY graph (still valid).
        if (tools == null || tools.size() <= 1) {
            log.info("[DepGraph] prefix={} toolCount={} — trivial graph, skipping LLM", serverIdPrefix,
                    tools == null ? 0 : tools.size());
            return new ToolDependencyGraph(serverIdPrefix, Map.of(), fingerprint, Instant.now(),
                    DependencyGraphStatus.READY);
        }

        Set<String> validNames = tools.stream().map(McpTool::name).collect(Collectors.toSet());
        StringBuilder systemPromptBuilder = new StringBuilder(promptLoader.getToolDependencyGraphPrompt())
                .append("\n\n## Tools for this server\n")
                .append(buildToolCatalog(tools));
        skillPromptLoader.findByServerIdPrefix(serverIdPrefix).ifPresent(skill ->
                systemPromptBuilder.append("\n\n## Server workflow guidance\n").append(skill));
        String systemPrompt = systemPromptBuilder.toString();
        List<Message> request = List.of(syntheticUserMessage(
                "Generate the dependency graph JSON for the tools listed in the system prompt."));

        log.info("[DepGraph] Generating graph prefix={} toolCount={} fingerprint={}",
                serverIdPrefix, tools.size(), fingerprint);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                long startMs = System.currentTimeMillis();
                LlmCompletionResult result = llmService.complete(
                        new LlmCompletionCommand(request, List.of(), null, false, systemPrompt));
                Map<String, List<String>> edges = responseParser.parse(result.content(), validNames);
                log.info("[DepGraph] READY prefix={} attempt={} edges={} elapsedMs={}",
                        serverIdPrefix, attempt, edges.size(), System.currentTimeMillis() - startMs);
                return new ToolDependencyGraph(serverIdPrefix, edges, fingerprint, Instant.now(),
                        DependencyGraphStatus.READY);
            } catch (Exception e) {
                log.warn("[DepGraph] Generation attempt {}/{} failed prefix={}: {}",
                        attempt, MAX_ATTEMPTS, serverIdPrefix, e.getMessage());
            }
        }

        log.error("[DepGraph] FAILED prefix={} after {} attempts — chat will use router-only selection",
                serverIdPrefix, MAX_ATTEMPTS);
        return new ToolDependencyGraph(serverIdPrefix, Map.of(), fingerprint, Instant.now(),
                DependencyGraphStatus.FAILED);
    }

    /** One compact line per tool: name, description, and required parameter names. */
    //The LLM uses this to reason about which tools need the output of other tools.
    private String buildToolCatalog(List<McpTool> tools) {
        StringBuilder builder = new StringBuilder();
        for (McpTool tool : tools) {
            builder.append("- ").append(tool.name()).append(": ").append(tool.description());
            List<String> required = requiredParams(tool.inputSchemaJson());
            if (!required.isEmpty()) {
                builder.append(" [required params: ").append(String.join(", ", required)).append("]");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private List<String> requiredParams(String inputSchemaJson) {
        List<String> required = new ArrayList<>();
        try {
            JsonNode schema = OBJECT_MAPPER.readTree(inputSchemaJson);
            if (schema.isTextual()) {
                schema = OBJECT_MAPPER.readTree(schema.asText());
            }
            JsonNode requiredNode = schema.path("required");
            if (requiredNode.isArray()) {
                requiredNode.forEach(node -> required.add(node.asText()));
            }
        } catch (Exception ignored) {
            // Schema is optional context for the prompt; ignore unparseable schemas.
        }
        return required;
    }

    private Message syntheticUserMessage(String content) {
        return new Message(
                UUID.randomUUID().toString(),
                "dependency-graph",
                MessageRole.USER,
                content,
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    /** Stable hash of the sorted tool-name set, used to detect when a stored graph is stale. */
    private String fingerprint(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "empty";
        }
        String joined = tools.stream().map(McpTool::name).sorted().collect(Collectors.joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(joined.hashCode());
        }
    }
}
