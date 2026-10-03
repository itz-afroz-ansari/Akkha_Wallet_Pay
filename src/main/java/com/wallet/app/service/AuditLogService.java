package com.wallet.app.service;

import com.wallet.app.entity.AuditLog;
import com.wallet.app.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;
    public AuditLogService(AuditLogRepository repository) { this.repository = repository; }

    @Transactional
    public void record(Long actorId, String action, String entityType, String entityId, String details) {
        repository.save(new AuditLog(actorId, action, entityType, entityId,
                details == null || details.length() <= 240 ? details : details.substring(0, 240), LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent() { return repository.findTop50ByOrderByCreatedAtDesc(); }
}
