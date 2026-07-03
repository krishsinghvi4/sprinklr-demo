package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared heuristics for checking whether a tool argument value was explicitly mentioned in the
 * conversation or discovered from same-turn tool results.
 */
public final class UserPromptValueMatcher {

    public static final String TOOL_RESULTS_THIS_TURN_MARKER = "[tool-results-this-turn:";
    public static final String TOOL_RESULTS_THIS_BATCH_MARKER = "[tool-results-this-batch:";

    private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile(
            "\\bbranch\\s+([\\w./-]+)|\\bon\\s+([\\w./-]+)\\s+branch\\b",
            Pattern.CASE_INSENSITIVE
    );

    private UserPromptValueMatcher() {
    }

    public static boolean valueAllowed(String conversationContext, String value) {
        return userMentionedValue(conversationContext, value)
                || appearsInToolResults(conversationContext, value);
    }

    public static boolean appearsInToolResults(String conversationContext, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (conversationContext == null || conversationContext.isBlank()) {
            return false;
        }

        String toolResultsText = extractToolResultsSections(conversationContext);
        if (toolResultsText.isBlank()) {
            return false;
        }

        String normalizedResults = normalize(toolResultsText);
        String normalizedValue = normalize(value);
        if (normalizedValue.length() <= 2 && !value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (normalizedResults.contains(normalizedValue)) {
            return true;
        }

        String decodedValue = normalize(decodeUrlPath(value));
        if (!decodedValue.equals(normalizedValue) && normalizedResults.contains(decodedValue)) {
            return true;
        }

        if (pathSegmentsMentioned(normalizedResults, decodedValue)) {
            return true;
        }

        // Match JSON-style numeric fields: "project_id":198, "iid":263763
        if (value.chars().allMatch(Character::isDigit)) {
            String raw = toolResultsText.toLowerCase(Locale.ROOT);
            return raw.contains("\"" + value + "\"")
                    || raw.contains(":" + value)
                    || raw.contains(":" + value + ",")
                    || raw.contains(":" + value + "}");
        }

        return false;
    }

    public static boolean userMentionedValue(String userPrompt, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return false;
        }

        String normalizedPrompt = normalize(userPrompt);
        String normalizedValue = normalize(value);
        if (normalizedPrompt.contains(normalizedValue)) {
            return true;
        }

        String decodedValue = normalize(decodeUrlPath(value));
        if (!decodedValue.equals(normalizedValue) && normalizedPrompt.contains(decodedValue)) {
            return true;
        }

        if (pathSegmentsMentioned(normalizedPrompt, decodedValue)) {
            return true;
        }

        String[] tokens = normalizedValue.split("\\s+");
        if (tokens.length <= 1) {
            return false;
        }
        int matched = 0;
        for (String token : tokens) {
            if (token.length() > 2 && normalizedPrompt.contains(token)) {
                matched++;
            }
        }
        return matched >= tokens.length - 1;
    }

    /**
     * For slash-separated paths (e.g. group/subgroup/repo), require every significant segment
     * to appear in the conversation text.
     */
    private static boolean pathSegmentsMentioned(String normalizedPrompt, String decodedPath) {
        if (decodedPath == null || !decodedPath.contains("/")) {
            return false;
        }
        String[] segments = decodedPath.split("/");
        int required = 0;
        int matched = 0;
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            String normalizedSegment = normalize(segment);
            if (normalizedSegment.length() <= 2) {
                continue;
            }
            required++;
            if (normalizedPrompt.contains(normalizedSegment)) {
                matched++;
            }
        }
        return required > 0 && matched >= required;
    }

    public static String branchNameMentionedInPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return null;
        }
        Matcher matcher = BRANCH_NAME_PATTERN.matcher(userPrompt);
        if (!matcher.find()) {
            return null;
        }
        String branch = matcher.group(1);
        if (branch == null || branch.isBlank()) {
            branch = matcher.group(2);
        }
        return branch != null && !branch.isBlank() ? branch.trim() : null;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
    }

    private static String decodeUrlPath(String value) {
        return value.replace("%2F", "/").replace("%2f", "/");
    }

    private static String extractToolResultsSections(String conversationContext) {
        StringBuilder sections = new StringBuilder();
        appendMarkedSection(conversationContext, TOOL_RESULTS_THIS_TURN_MARKER, sections);
        appendMarkedSection(conversationContext, TOOL_RESULTS_THIS_BATCH_MARKER, sections);
        return sections.toString();
    }

    private static void appendMarkedSection(String context, String marker, StringBuilder sections) {
        int start = context.indexOf(marker);
        while (start >= 0) {
            int contentStart = start + marker.length();
            int end = context.indexOf(']', contentStart);
            if (end < 0) {
                break;
            }
            if (sections.length() > 0) {
                sections.append('\n');
            }
            sections.append(context, contentStart, end);
            start = context.indexOf(marker, end + 1);
        }
    }
}
