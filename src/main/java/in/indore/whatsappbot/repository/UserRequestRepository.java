package in.indore.whatsappbot.repository;

import in.indore.whatsappbot.model.UserRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRequestRepository extends JpaRepository<UserRequests, UUID> {

    @Query(value = """
        SELECT u.phone
        FROM public.user_request u
        INNER JOIN grievances g ON CAST(u.id AS varchar) = g.user_request_id
        WHERE g.grievance_number = :grievanceNumber
        LIMIT 1
      """, nativeQuery = true)
    Optional<String> findPhoneByGrievanceId(@Param("grievanceNumber") String grievanceNumber);

}
