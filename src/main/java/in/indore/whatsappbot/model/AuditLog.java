package in.indore.whatsappbot.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    private String phone;
    private String direction;
    @Column(columnDefinition = "text")
    private String message;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawPayload;
    private Instant createdAt;

    public AuditLog() {}
    public AuditLog(String phone, String direction, String message, String rawPayload) {
        this.id = UUID.randomUUID();
        this.phone = phone;
        this.direction = direction;
        this.message = message;
        this.rawPayload = rawPayload;
        this.createdAt = Instant.now();
    }

    @PrePersist public void pre(){ if(id==null) id=UUID.randomUUID(); if(createdAt==null) createdAt=Instant.now(); }

    public UUID getId(){ return id; }
    public String getPhone(){ return phone; }
    public String getDirection(){ return direction; }
    public String getMessage(){ return message; }
    public String getRawPayload(){ return rawPayload; }
    public Instant getCreatedAt(){ return createdAt; }
}
