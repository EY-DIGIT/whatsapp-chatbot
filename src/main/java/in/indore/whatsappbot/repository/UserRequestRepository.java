package in.indore.whatsappbot.repository;

import in.indore.whatsappbot.model.UserRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserRequestRepository extends JpaRepository<UserRequests, UUID> {}
