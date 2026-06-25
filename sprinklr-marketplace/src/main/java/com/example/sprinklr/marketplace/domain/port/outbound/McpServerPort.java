package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;

public interface McpServerPort {


    McpInvocationResult invoke(McpInvocation invocation);

}
