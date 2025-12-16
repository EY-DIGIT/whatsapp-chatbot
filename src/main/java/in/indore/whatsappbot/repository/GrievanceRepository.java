package in.indore.whatsappbot.repository;

import in.indore.whatsappbot.model.Grievance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrievanceRepository extends JpaRepository<Grievance, UUID> {}
