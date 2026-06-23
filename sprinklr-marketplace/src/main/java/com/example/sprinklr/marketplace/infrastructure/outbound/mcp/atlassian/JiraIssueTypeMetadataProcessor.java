package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses Jira create-field metadata from {@code getJiraIssueTypeMetaWithFields},
 * merges paginated field pages, and compacts the payload so the LLM can see every
 * {@code required: true} field without scanning hundreds of KB of option data.
 */
public final class JiraIssueTypeMetadataProcessor {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTypeMetadataProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OPTIONS_PER_FIELD = 40;
    private static final Set<String> CREATE_ARG_SYSTEM_FIELDS = Set.of("project", "issuetype");
    private static final Set<String> TOP_LEVEL_SYSTEM_FIELDS = Set.of("summary", "description");
    private static final Set<String> SKIP_ON_CREATE_FIELDS = Set.of("issuelinks", "parent");

    private JiraIssueTypeMetadataProcessor() {
    }

    public record ProcessedMetadata(
            JsonNode mergedRoot,
            int topLevelFieldCount,
            int requiredFieldCount,
            boolean paginationIncomplete,
            int reportedTotal,
            int fetchedFieldCount
    ) {}

    public record RequiredField(
            String fieldId,
            String name,
            String placement,
            String additionalFieldsKey
    ) {}

    public record CreateScope(String projectKey, String issueTypeName, String issueTypeId) {

        public List<String> issueTypeLookupKeys() {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            if (issueTypeName != null && !issueTypeName.isBlank()) {
                keys.add(issueTypeName);
            }
            if (issueTypeId != null && !issueTypeId.isBlank()) {
                keys.add(issueTypeId);
            }
            return List.copyOf(keys);
        }
    }

    public record IssueTypeIdentity(String id, String name) {}

    public record IssueTypeRef(String id, String name) {}

    /**
     * Merges additional paginated field pages into the first MCP result node.
     */
    public static ProcessedMetadata mergePages(JsonNode firstPage, List<JsonNode> additionalPages) {
        ObjectNode merged = firstPage.isObject()
                ? ((ObjectNode) firstPage).deepCopy()
                : MAPPER.createObjectNode();

        Map<String, JsonNode> fieldsById = new LinkedHashMap<>();
        collectTopLevelFields(merged, fieldsById);

        for (JsonNode page : additionalPages) {
            collectTopLevelFields(page, fieldsById);
        }

        ObjectNode fieldsObject = MAPPER.createObjectNode();
        fieldsById.forEach(fieldsObject::set);
        merged.set("fields", fieldsObject);

        int reportedTotal = merged.path("total").asInt(fieldsById.size());
        boolean paginationIncomplete = reportedTotal > fieldsById.size();

        return new ProcessedMetadata(
                merged,
                fieldsById.size(),
                countRequired(fieldsById),
                paginationIncomplete,
                reportedTotal,
                fieldsById.size()
        );
    }

    public static ProcessedMetadata analyzeSinglePage(JsonNode page) {
        Map<String, JsonNode> fieldsById = new LinkedHashMap<>();
        collectTopLevelFields(page, fieldsById);
        int reportedTotal = page.path("total").asInt(fieldsById.size());
        return new ProcessedMetadata(
                page,
                fieldsById.size(),
                countRequired(fieldsById),
                reportedTotal > fieldsById.size(),
                reportedTotal,
                fieldsById.size()
        );
    }

    /**
     * Required fields the user must supply, extracted from merged metadata (same set as {@code requiredUserInput}).
     */
    public static List<RequiredField> requiredUserInputFields(ProcessedMetadata processed) {
        Map<String, JsonNode> fieldsById = new LinkedHashMap<>();
        collectTopLevelFields(processed.mergedRoot(), fieldsById);
        List<RequiredField> required = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : fieldsById.entrySet()) {
            JsonNode field = entry.getValue();
            if (!field.path("required").asBoolean(false)) {
                continue;
            }
            ObjectNode fieldSummary = toFieldSummary(entry.getKey(), field);
            if (!fieldSummary.path("askUser").asBoolean(false)) {
                continue;
            }
            required.add(new RequiredField(
                    fieldSummary.path("fieldId").asText(),
                    fieldSummary.path("name").asText(),
                    fieldSummary.path("placement").asText(),
                    fieldSummary.path("additionalFieldsKey").asText()
            ));
        }
        return required;
    }

    /**
     * Parses project + issue type from metadata or create tool arguments.
     */
    public static Optional<CreateScope> parseCreateScope(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return Optional.empty();
            }
            String projectKey = firstNonBlankOrNull(
                    root.path("projectKey").asText(null),
                    root.path("projectIdOrKey").asText(null),
                    root.path("projectId").asText(null),
                    root.path("projectKeyOrId").asText(null)
            );
            if (projectKey == null) {
                return Optional.empty();
            }
            String issueTypeName = firstNonBlankOrNull(
                    root.path("issueTypeName").asText(null),
                    root.path("issueType").asText(null),
                    root.path("type").asText(null),
                    root.path("issue_type").asText(null)
            );
            String issueTypeId = firstNonBlankOrNull(root.path("issueTypeId").asText(null));
            if (issueTypeName == null && issueTypeId == null) {
                return Optional.empty();
            }
            return Optional.of(new CreateScope(projectKey, issueTypeName, issueTypeId));
        } catch (Exception exception) {
            log.warn("[JiraMetadata] Failed to parse create scope: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves project + issue type from tool arguments, enriched from the metadata response when possible.
     */
    public static Optional<CreateScope> resolveCreateScope(String argumentsJson, ProcessedMetadata processed) {
        Optional<CreateScope> fromArgs = parseCreateScope(argumentsJson);
        Optional<CreateScope> fromResponse = extractScopeFromMetadataResponse(processed);
        if (fromArgs.isPresent()) {
            return Optional.of(enrichScope(fromArgs.get(), fromResponse, processed));
        }
        return fromResponse;
    }

    public static Optional<String> parseProjectKeyFromArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return Optional.empty();
            }
            return Optional.ofNullable(firstNonBlankOrNull(
                    root.path("projectKey").asText(null),
                    root.path("projectIdOrKey").asText(null),
                    root.path("projectId").asText(null),
                    root.path("projectKeyOrId").asText(null)
            ));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /**
     * Parses issue types from a {@code getJiraProjectIssueTypesMetadata} tool result.
     */
    public static List<IssueTypeRef> parseProjectIssueTypesResponse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(rawContent);
            LinkedHashSet<IssueTypeRef> refs = new LinkedHashSet<>();
            collectIssueTypeRefs(root.path("issueTypes"), refs);
            collectIssueTypeRefs(root.path("issuetypes"), refs);
            collectIssueTypeRefs(root.path("values"), refs);
            JsonNode projects = root.path("projects");
            if (projects.isArray()) {
                for (JsonNode project : projects) {
                    collectIssueTypeRefs(project.path("issuetypes"), refs);
                    collectIssueTypeRefs(project.path("issueTypes"), refs);
                }
            }
            return List.copyOf(refs);
        } catch (Exception exception) {
            log.warn("[JiraMetadata] Failed to parse project issue types: {}", exception.getMessage());
            return List.of();
        }
    }

    public static Optional<CreateScope> extractScopeFromMetadataResponse(ProcessedMetadata processed) {
        JsonNode projects = processed.mergedRoot().path("projects");
        if (!projects.isArray()) {
            return Optional.empty();
        }
        CreateScope soleMatch = null;
        int matchCount = 0;
        for (JsonNode project : projects) {
            String projectKey = firstNonBlankOrNull(
                    project.path("key").asText(null),
                    project.path("id").asText(null)
            );
            if (projectKey == null) {
                continue;
            }
            JsonNode issueTypes = project.path("issuetypes");
            if (!issueTypes.isArray()) {
                issueTypes = project.path("issueTypes");
            }
            if (!issueTypes.isArray()) {
                continue;
            }
            for (JsonNode issueType : issueTypes) {
                if (!hasFieldsNode(issueType)) {
                    continue;
                }
                String id = firstNonBlankOrNull(issueType.path("id").asText(null));
                String name = firstNonBlankOrNull(issueType.path("name").asText(null));
                if (id == null && name == null) {
                    continue;
                }
                soleMatch = new CreateScope(projectKey, name, id);
                matchCount++;
            }
        }
        if (matchCount == 1 && soleMatch != null) {
            return Optional.of(soleMatch);
        }
        return Optional.empty();
    }

    public static Optional<IssueTypeIdentity> extractIssueTypeIdentity(ProcessedMetadata processed) {
        return extractIssueTypeIdentity(processed, null, null, null);
    }

    public static Optional<IssueTypeIdentity> extractIssueTypeIdentity(
            ProcessedMetadata processed,
            String projectKeyHint,
            String issueTypeNameHint,
            String issueTypeIdHint
    ) {
        JsonNode projects = processed.mergedRoot().path("projects");
        if (!projects.isArray()) {
            return Optional.empty();
        }
        for (JsonNode project : projects) {
            String projectKey = firstNonBlankOrNull(
                    project.path("key").asText(null),
                    project.path("id").asText(null)
            );
            if (projectKeyHint != null && !projectKeyHint.isBlank()
                    && projectKey != null
                    && !projectKey.equalsIgnoreCase(projectKeyHint)) {
                continue;
            }
            JsonNode issueTypes = project.path("issuetypes");
            if (!issueTypes.isArray()) {
                issueTypes = project.path("issueTypes");
            }
            if (!issueTypes.isArray()) {
                continue;
            }
            IssueTypeIdentity fallback = null;
            for (JsonNode issueType : issueTypes) {
                String id = firstNonBlankOrNull(issueType.path("id").asText(null));
                String name = firstNonBlankOrNull(issueType.path("name").asText(null));
                if (id == null && name == null) {
                    continue;
                }
                IssueTypeIdentity candidate = new IssueTypeIdentity(id, name);
                if (issueTypeIdHint != null && id != null && issueTypeIdHint.equals(id)) {
                    return Optional.of(candidate);
                }
                if (issueTypeNameHint != null && name != null
                        && issueTypeNameHint.equalsIgnoreCase(name)) {
                    return Optional.of(candidate);
                }
                if (hasFieldsNode(issueType) && fallback == null) {
                    fallback = candidate;
                }
            }
            if (fallback != null) {
                return Optional.of(fallback);
            }
        }
        return Optional.empty();
    }

    /**
     * Field key → valueShape for all additional_fields entries from the merged metadata page.
     */
    public static Map<String, String> fieldShapes(ProcessedMetadata processed) {
        Map<String, JsonNode> fieldsById = new LinkedHashMap<>();
        collectTopLevelFields(processed.mergedRoot(), fieldsById);
        Map<String, String> shapes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : fieldsById.entrySet()) {
            String key = resolveFieldKey(entry.getKey(), entry.getValue());
            if (classifyPlacement(key, entry.getValue()) != FieldPlacement.ADDITIONAL_FIELDS) {
                continue;
            }
            shapes.put(key, inferValueShape(entry.getValue().path("schema")));
        }
        return shapes;
    }

    /**
     * @return compact JSON for the LLM tool result, or empty when parsing fails
     */
    public static Optional<String> summarize(ProcessedMetadata processed) {
        return summarize(processed, Optional.empty());
    }

    public static Optional<String> summarize(ProcessedMetadata processed, Optional<CreateScope> scope) {
        try {
            Map<String, JsonNode> fieldsById = new LinkedHashMap<>();
            collectTopLevelFields(processed.mergedRoot(), fieldsById);

            ArrayNode requiredFields = MAPPER.createArrayNode();
            ArrayNode requiredUserInput = MAPPER.createArrayNode();
            for (Map.Entry<String, JsonNode> entry : fieldsById.entrySet()) {
                JsonNode field = entry.getValue();
                if (!field.path("required").asBoolean(false)) {
                    continue;
                }
                ObjectNode fieldSummary = toFieldSummary(entry.getKey(), field);
                requiredFields.add(fieldSummary);
                if (fieldSummary.path("askUser").asBoolean(false)) {
                    requiredUserInput.add(fieldSummary);
                }
            }

            ObjectNode summary = MAPPER.createObjectNode();
            summary.put("metadataKind", "jiraIssueTypeCreateFieldsSummary");
            scope.ifPresent(createScope -> {
                summary.put("projectKey", createScope.projectKey());
                if (createScope.issueTypeName() != null) {
                    summary.put("issueTypeName", createScope.issueTypeName());
                }
                if (createScope.issueTypeId() != null) {
                    summary.put("issueTypeId", createScope.issueTypeId());
                }
            });
            summary.put("topLevelFieldCount", processed.topLevelFieldCount());
            summary.put("requiredFieldCount", requiredFields.size());
            summary.put("requiredUserInputCount", requiredUserInput.size());
            summary.put("reportedTotal", processed.reportedTotal());
            summary.put("paginationIncomplete", processed.paginationIncomplete());
            if (processed.paginationIncomplete()) {
                summary.put(
                        "paginationWarning",
                        "Field metadata may be incomplete — Jira reported "
                                + processed.reportedTotal()
                                + " fields but only "
                                + processed.fetchedFieldCount()
                                + " were returned. Required fields may be missing from this summary."
                );
            }
            summary.set("requiredForCreate", requiredFields);
            summary.set("requiredUserInput", requiredUserInput);
            ObjectNode shapeNode = MAPPER.createObjectNode();
            fieldShapes(processed).forEach(shapeNode::put);
            summary.set("fieldValueShapes", shapeNode);
            summary.put(
                    "usage",
                    "Use additionalFieldsKey (never the display name) inside additional_fields. "
                            + "Follow placement: top_level → summary/description params; create_args → projectKey/issueTypeName; "
                            + "additional_fields → customfield_* and fixVersions; skip → do not send. "
                            + "Match valueShape exactly."
            );

            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
            log.info(
                    "[JiraMetadata] Summarized create metadata topLevelFields={} requiredFields={} paginationIncomplete={} summaryLen={}",
                    processed.topLevelFieldCount(),
                    processed.requiredFieldCount(),
                    processed.paginationIncomplete(),
                    json.length()
            );
            return Optional.of(json);
        } catch (Exception exception) {
            log.warn("[JiraMetadata] Failed to summarize metadata: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public static boolean needsMorePages(JsonNode page, int fetchedCount) {
        if (page == null || !page.isObject()) {
            return false;
        }
        if (page.has("isLast") && !page.path("isLast").asBoolean(true)) {
            return true;
        }
        int total = page.path("total").asInt(-1);
        if (total > 0 && fetchedCount < total) {
            return true;
        }
        int startAt = page.path("startAt").asInt(0);
        int maxResults = page.path("maxResults").asInt(0);
        if (total > 0 && maxResults > 0 && startAt + maxResults < total) {
            return true;
        }
        return false;
    }

    public static int nextStartAt(JsonNode page, int fetchedCount) {
        if (page.has("startAt") && page.has("maxResults")) {
            return page.path("startAt").asInt(0) + page.path("maxResults").asInt(fetchedCount);
        }
        return fetchedCount;
    }

    private static void collectTopLevelFields(JsonNode root, Map<String, JsonNode> fieldsById) {
        JsonNode fieldsNode = findFieldsNode(root);
        if (fieldsNode == null) {
            return;
        }
        if (fieldsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = fieldsNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                fieldsById.put(resolveFieldKey(entry.getKey(), entry.getValue()), entry.getValue());
            }
            return;
        }
        if (fieldsNode.isArray()) {
            for (JsonNode field : fieldsNode) {
                fieldsById.put(resolveFieldKey(null, field), field);
            }
        }
    }

    private static JsonNode findFieldsNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.has("fields")) {
            return root.get("fields");
        }
        JsonNode projects = root.path("projects");
        if (projects.isArray()) {
            for (JsonNode project : projects) {
                JsonNode issueTypes = project.path("issuetypes");
                if (!issueTypes.isArray()) {
                    continue;
                }
                for (JsonNode issueType : issueTypes) {
                    JsonNode fields = issueType.path("fields");
                    if (!fields.isMissingNode() && !fields.isNull()) {
                        return fields;
                    }
                }
            }
        }
        return null;
    }

    private static int countRequired(Map<String, JsonNode> fieldsById) {
        int count = 0;
        for (JsonNode field : fieldsById.values()) {
            if (field.path("required").asBoolean(false)) {
                count++;
            }
        }
        return count;
    }

    private static ObjectNode toFieldSummary(String mapKey, JsonNode field) {
        ObjectNode summary = MAPPER.createObjectNode();
        String resolvedKey = resolveFieldKey(mapKey, field);
        String displayName = field.path("name").asText(resolvedKey);
        FieldPlacement placement = classifyPlacement(resolvedKey, field);

        summary.put("fieldId", resolvedKey);
        summary.put("additionalFieldsKey", placement == FieldPlacement.ADDITIONAL_FIELDS ? resolvedKey : "");
        summary.put("name", displayName);
        if (field.has("key") && !field.path("key").asText("").equals(resolvedKey)) {
            summary.put("key", field.path("key").asText());
        }
        summary.put("required", true);
        summary.put("placement", placement.wireName());
        summary.put("askUser", placement.askUser());
        if (field.has("hasDefaultValue")) {
            summary.put("hasDefaultValue", field.path("hasDefaultValue").asBoolean());
        }
        JsonNode schema = field.path("schema");
        if (!schema.isMissingNode()) {
            summary.put("schemaType", schema.path("type").asText(""));
            if (schema.has("system")) {
                summary.put("schemaSystem", schema.path("system").asText());
            }
            if (schema.has("custom")) {
                summary.put("schemaCustom", schema.path("custom").asText());
            }
        }
        summary.put("valueShape", inferValueShape(schema));
        ArrayNode allowedOptions = extractAllowedOptions(field);
        if (!allowedOptions.isEmpty()) {
            summary.set("allowedOptions", allowedOptions);
        }
        return summary;
    }

    private enum FieldPlacement {
        TOP_LEVEL("top_level", true),
        CREATE_ARGS("create_args", false),
        ADDITIONAL_FIELDS("additional_fields", true),
        SKIP("skip", false);

        private final String wireName;
        private final boolean askUser;

        FieldPlacement(String wireName, boolean askUser) {
            this.wireName = wireName;
            this.askUser = askUser;
        }

        String wireName() {
            return wireName;
        }

        boolean askUser() {
            return askUser;
        }
    }

    private static FieldPlacement classifyPlacement(String resolvedKey, JsonNode field) {
        String system = field.path("schema").path("system").asText(resolvedKey).toLowerCase();
        String normalizedKey = resolvedKey.toLowerCase();
        if (SKIP_ON_CREATE_FIELDS.contains(normalizedKey) || "issuelinks".equals(system)) {
            return FieldPlacement.SKIP;
        }
        if (CREATE_ARG_SYSTEM_FIELDS.contains(normalizedKey)
                || "project".equals(system)
                || "issuetype".equals(system)) {
            return FieldPlacement.CREATE_ARGS;
        }
        if (TOP_LEVEL_SYSTEM_FIELDS.contains(normalizedKey)
                || "summary".equals(system)
                || "description".equals(system)) {
            return FieldPlacement.TOP_LEVEL;
        }
        return FieldPlacement.ADDITIONAL_FIELDS;
    }

    private static String inferValueShape(JsonNode schema) {
        String type = schema.path("type").asText("");
        String items = schema.path("items").asText("");
        if ("array".equals(type)) {
            if ("version".equals(items)) {
                return "version_array";
            }
            if ("option".equals(items) || "string".equals(items) || items.isBlank()) {
                return "option_array";
            }
            return "option_array";
        }
        if ("option".equals(type) || "priority".equals(schema.path("system").asText())) {
            return "option_object";
        }
        if ("string".equals(type) || "any".equals(type)) {
            return "string";
        }
        return "option_object";
    }

    private static String resolveFieldKey(String mapKey, JsonNode field) {
        String fromField = firstNonBlank(
                field.path("fieldId").asText(null),
                field.path("key").asText(null)
        );
        if (isJiraFieldKey(fromField)) {
            return fromField;
        }
        if (isJiraFieldKey(mapKey)) {
            return mapKey;
        }
        return fromField != null ? fromField : mapKey;
    }

    private static boolean isJiraFieldKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (key.startsWith("customfield_")) {
            return true;
        }
        if (key.contains(" ")) {
            return false;
        }
        return true;
    }

    private static ArrayNode extractAllowedOptions(JsonNode field) {
        ArrayNode options = MAPPER.createArrayNode();
        appendOptions(field.path("allowedValues"), options);
        appendOptions(field.path("allowedvalues"), options);
        return options;
    }

    private static void appendOptions(JsonNode allowedValues, ArrayNode options) {
        if (!allowedValues.isArray() || options.size() >= MAX_OPTIONS_PER_FIELD) {
            return;
        }
        for (JsonNode option : allowedValues) {
            if (options.size() >= MAX_OPTIONS_PER_FIELD) {
                break;
            }
            if (option.isTextual()) {
                options.add(option.asText());
                continue;
            }
            String value = firstNonBlank(
                    option.path("value").asText(null),
                    option.path("name").asText(null),
                    option.path("label").asText(null)
            );
            if (value != null) {
                options.add(value);
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private static String firstNonBlankOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static CreateScope enrichScope(
            CreateScope fromArgs,
            Optional<CreateScope> fromResponse,
            ProcessedMetadata processed
    ) {
        String projectKey = fromArgs.projectKey();
        String issueTypeName = fromArgs.issueTypeName();
        String issueTypeId = fromArgs.issueTypeId();

        Optional<IssueTypeIdentity> identity = extractIssueTypeIdentity(
                processed,
                projectKey,
                issueTypeName,
                issueTypeId
        );
        if (identity.isPresent()) {
            IssueTypeIdentity resolved = identity.get();
            if (issueTypeName == null) {
                issueTypeName = resolved.name();
            }
            if (issueTypeId == null) {
                issueTypeId = resolved.id();
            }
        } else if (fromResponse.isPresent()) {
            CreateScope responseScope = fromResponse.get();
            if (issueTypeName == null) {
                issueTypeName = responseScope.issueTypeName();
            }
            if (issueTypeId == null) {
                issueTypeId = responseScope.issueTypeId();
            }
        }
        return new CreateScope(projectKey, issueTypeName, issueTypeId);
    }

    private static void collectIssueTypeRefs(JsonNode issueTypesNode, Set<IssueTypeRef> refs) {
        if (!issueTypesNode.isArray()) {
            return;
        }
        for (JsonNode issueType : issueTypesNode) {
            String id = firstNonBlankOrNull(issueType.path("id").asText(null));
            String name = firstNonBlankOrNull(issueType.path("name").asText(null));
            if (id != null || name != null) {
                refs.add(new IssueTypeRef(id, name));
            }
        }
    }

    private static boolean hasFieldsNode(JsonNode issueType) {
        JsonNode fields = issueType.path("fields");
        return !fields.isMissingNode() && !fields.isNull();
    }
}
