package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores Sprinklr LLM-router PII placeholders (e.g. {@code !$SPR_PII_Email_Address@1$!})
 * using real values taken from user text that was never masked in this service.
 * <p>
 * The router masks emails before the model runs; tool_call arguments echo the placeholders.
 * MCP calls need the real values.
 */
public final class SprPiiPlaceholderRestorer {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    );
    private static final Pattern EMAIL_PLACEHOLDER = Pattern.compile(
            "!\\$SPR_PII_Email_Address@(\\d+)\\$!"
    );

    private SprPiiPlaceholderRestorer() {
    }

    public static boolean containsEmailPlaceholder(String text) {
        return text != null && EMAIL_PLACEHOLDER.matcher(text).find();
    }

    /**
     * Replaces {@code !$SPR_PII_Email_Address@N$!} with the Nth email (1-indexed)
     * found across {@code sourceTexts} in order.
     */
    public static String restoreEmailPlaceholders(String text, List<String> sourceTexts) {
        if (text == null || text.isBlank() || !containsEmailPlaceholder(text)) {
            return text;
        }
        List<String> emails = extractEmails(sourceTexts);
        if (emails.isEmpty()) {
            return text;
        }
        Matcher matcher = EMAIL_PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = (index >= 1 && index <= emails.size())
                    ? emails.get(index - 1)
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static List<String> extractEmails(List<String> sourceTexts) {
        List<String> emails = new ArrayList<>();
        if (sourceTexts == null) {
            return emails;
        }
        for (String source : sourceTexts) {
            if (source == null || source.isBlank()) {
                continue;
            }
            Matcher matcher = EMAIL.matcher(source);
            while (matcher.find()) {
                emails.add(matcher.group());
            }
        }
        return emails;
    }
}
