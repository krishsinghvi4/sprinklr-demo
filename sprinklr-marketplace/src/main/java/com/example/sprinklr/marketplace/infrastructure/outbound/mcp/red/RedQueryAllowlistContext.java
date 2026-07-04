package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

/**
 * Optional RED query allowlist markdown to append under the RED skill section.
 */
public record RedQueryAllowlistContext(String markdownSection) {

    public static RedQueryAllowlistContext empty() {
        return new RedQueryAllowlistContext(null);
    }

    public boolean hasContent() {
        return markdownSection != null && !markdownSection.isBlank();
    }
}
