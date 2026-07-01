package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.insights.DashboardConversation;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;

import java.util.List;
import java.util.Optional;

public interface InsightsDashboardPort {

    List<DashboardConversation> findConversationsByUserId(String userId);

    Optional<DashboardConversation> findConversationByIdAndUserId(String id, String userId);

    Optional<DashboardConversation> findConversationBySourceConversationId(String userId, String sourceConversationId);

    DashboardConversation saveConversation(DashboardConversation conversation);

    void deleteConversation(String id, String userId);

    List<DashboardTurn> findTurnsByDashboardConversationId(String dashboardConversationId, String userId);

    Optional<DashboardTurn> findTurnById(String turnId, String userId);

    DashboardTurn saveTurn(DashboardTurn turn);

    void deleteTurn(String turnId, String userId);

    void touchConversation(String dashboardConversationId, String preview);
}
