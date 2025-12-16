package in.indore.whatsappbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "language")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Language {
    @Id
    private String phone;
    private String language;
    private Instant updatedAt;

    public Language(String phone, String language) { this.phone = phone; this.language = language; this.updatedAt = Instant.now(); }

    @PrePersist @PreUpdate public void touch(){ this.updatedAt = Instant.now(); }
}
