package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;

public interface McpServerPort {


    /*
    Explicit Responsibility:
    When the AI model determines it needs external data,
    it generates a tool execution request.
    The application core translates that request into an McpInvocation and hands it to this port.
    The implementation of this port (the adapter) is responsible for opening the HTTP or JSON-RPC network connection,
     securely passing the payload to the external system, waiting for the execution to finish,
     and returning the raw string data back to the core.
    */
    McpInvocationResult invoke(McpInvocation invocation);

}
