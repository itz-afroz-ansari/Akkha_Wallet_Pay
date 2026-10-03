package com.wallet.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_created", columnList = "created_at"))
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id", length = 64) private String entityId;
    @Column(length = 240) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected AuditLog() {}
    public AuditLog(Long actorUserId, String action, String entityType, String entityId, String details, LocalDateTime createdAt) {
        this.actorUserId = actorUserId; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.details = details; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
