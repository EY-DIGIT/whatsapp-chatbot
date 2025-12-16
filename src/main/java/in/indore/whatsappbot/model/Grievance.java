package in.indore.whatsappbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grievances")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Grievance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    private String grievance_number;
    private String status;
    private Instant createdAt;
    private String userRequestId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Grievance(String status, String userRequestId) {
        this.id = UUID.randomUUID();
        this.status = status;
        this.userRequestId = userRequestId;
    }
}
