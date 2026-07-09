package com.example.sprinklr.marketplace.domain.port.outbound.MCP;

import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocationResult;

public interface McpServerPort {


    McpInvocationResult invoke(McpInvocation invocation);

}
