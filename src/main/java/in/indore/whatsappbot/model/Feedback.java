package in.indore.whatsappbot.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    private String userRequestId;
    private Integer rating;
    private Instant createdAt;

    public Feedback() {}
    public Feedback(String userRequestId, Integer rating) {
        this.id = UUID.randomUUID(); this.userRequestId = userRequestId; this.rating = rating; this.createdAt = Instant.now();
    }

    public UUID getId(){ return id; }
    public String getUserRequestId(){ return userRequestId; }
    public Integer getRating(){ return rating; }
    public Instant getCreatedAt(){ return createdAt; }
}
