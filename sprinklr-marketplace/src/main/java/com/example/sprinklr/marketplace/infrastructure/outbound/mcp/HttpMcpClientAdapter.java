package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HttpMcpClientAdapter implements McpServerPort {

    // In a real enterprise app, you would inject a WebClient here to make HTTP POSTs.
    // For now, we simulate the parallel network call so you can test the system.

    @Override
    public McpInvocationResult invoke(McpInvocation invocation) {
        System.out.println("--> [NETWORK] Executing parallel call to " + invocation.serverId() + " for tool: " + invocation.toolName());
        
        /*try {
            // Simulating network latency (this will run in parallel thanks to your Orchestrator!)
            //Thread.sleep(1500); 
            String s;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }*/

        String fakeJsonResult = "{ \"status\": \"success\", \"data\": \"Simulated data from " + invocation.serverId() + "\" }";
        
        System.out.println("<-- [NETWORK] Received data from " + invocation.serverId());
        return new McpInvocationResult(UUID.randomUUID().toString(), true, fakeJsonResult, null);
    }
}