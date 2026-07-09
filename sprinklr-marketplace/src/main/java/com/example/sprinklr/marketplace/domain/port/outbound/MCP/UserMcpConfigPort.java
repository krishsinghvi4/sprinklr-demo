package com.example.sprinklr.marketplace.domain.port.outbound.MCP;

import com.example.sprinklr.marketplace.domain.model.MCP.UserMcpConfig;

import java.util.List;
import java.util.Optional;

public interface UserMcpConfigPort {

    UserMcpConfig save(UserMcpConfig config);

    List<UserMcpConfig> findByUserId(String userId);

    Optional<UserMcpConfig> findByIdAndUserId(String id, String userId);

    Optional<UserMcpConfig> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix);

    void deleteByIdAndUserId(String id, String userId);
}
