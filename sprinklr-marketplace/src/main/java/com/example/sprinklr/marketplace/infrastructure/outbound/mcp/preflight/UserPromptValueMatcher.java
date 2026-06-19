package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared heuristics for checking whether a tool argument value was explicitly mentioned in the user prompt.
 */
public final class UserPromptValueMatcher {

    private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile(
            "\\bbranch\\s+([\\w./-]+)|\\bon\\s+([\\w./-]+)\\s+branch\\b",
            Pattern.CASE_INSENSITIVE
    );

    private UserPromptValueMatcher() {
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
}
