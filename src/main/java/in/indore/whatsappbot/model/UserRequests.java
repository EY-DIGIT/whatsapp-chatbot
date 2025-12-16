package in.indore.whatsappbot.model;

import in.indore.whatsappbot.enums.RequestFor;
import in.indore.whatsappbot.enums.RequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_request")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_for", nullable = false, length = 10)
    private RequestFor requestFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30)
    private RequestType requestType;

    @Column(name = "owner_phone")
    private String ownerPhone;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verification_request_at")
    private Instant verificationRequestAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UserRequests(String phone, RequestFor requestFor, RequestType requestType, String ownerPhone) {
        this.id = UUID.randomUUID();
        this.phone = phone;
        this.requestFor = requestFor;
        this.requestType = requestType;
        this.ownerPhone = ownerPhone;
        this.createdAt = Instant.now();
    }

    public UserRequests(String phone, RequestFor requestFor) {
        this.phone = phone;
        this.requestFor = requestFor;
    }
}
