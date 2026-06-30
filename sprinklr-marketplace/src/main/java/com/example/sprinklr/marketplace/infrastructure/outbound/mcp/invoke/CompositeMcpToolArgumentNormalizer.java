package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompositeMcpToolArgumentNormalizer {

    private final List<McpToolArgumentNormalizer> normalizers;

    public CompositeMcpToolArgumentNormalizer(List<McpToolArgumentNormalizer> normalizers) {
        this.normalizers = normalizers;
    }

    public String normalize(
            McpCatalogEntry entry,
            String toolName,
            String argumentsJson,
            String connectionId
    ) {
        for (McpToolArgumentNormalizer normalizer : normalizers) {
            if (normalizer.supports(entry, toolName)) {
                return normalizer.normalize(entry, toolName, argumentsJson, connectionId);
            }
        }
        return argumentsJson;
    }
}
