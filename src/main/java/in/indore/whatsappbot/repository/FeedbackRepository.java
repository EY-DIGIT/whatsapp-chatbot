package in.indore.whatsappbot.repository;

import in.indore.whatsappbot.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {}
