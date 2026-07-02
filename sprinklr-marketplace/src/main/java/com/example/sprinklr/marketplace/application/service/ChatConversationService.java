package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.InsightsDashboardPort;
import com.example.sprinklr.marketplace.domain.port.outbound.PendingWorkflowPort;
import org.springframework.stereotype.Service;

@Service
public class ChatConversationService {

    private final ChatHistoryPort chatHistoryPort;
    private final InsightsDashboardPort insightsDashboardPort;
    private final PendingWorkflowPort pendingWorkflowPort;

    public ChatConversationService(
            ChatHistoryPort chatHistoryPort,
            InsightsDashboardPort insightsDashboardPort,
            PendingWorkflowPort pendingWorkflowPort
    ) {
        this.chatHistoryPort = chatHistoryPort;
        this.insightsDashboardPort = insightsDashboardPort;
        this.pendingWorkflowPort = pendingWorkflowPort;
    }

    public boolean deleteConversation(String conversationId, String userId) {
        if (chatHistoryPort.findConversationByIdAndUserId(conversationId, userId).isEmpty()) {
            return false;
        }

        insightsDashboardPort.deleteBySourceConversationId(userId, conversationId);
        pendingWorkflowPort.delete(conversationId);
        chatHistoryPort.deleteConversation(conversationId, userId);
        return true;
    }
}
