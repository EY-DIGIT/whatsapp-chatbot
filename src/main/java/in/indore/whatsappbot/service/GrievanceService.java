package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.Grievance;
import in.indore.whatsappbot.repository.GrievanceRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GrievanceService {

    private final GrievanceRepository grievanceRepository;

    public GrievanceService(GrievanceRepository grievanceRepository) {
        this.grievanceRepository = grievanceRepository;
    }

    public Grievance createGrievance(Grievance grievance) {
        return grievanceRepository.save(grievance);
    }

    public Grievance getGrievanceById(String id) {
        return grievanceRepository.findById(UUID.fromString(id)).orElse(null);
    }

    public void updateGrievance(String serviceRequestId, String status, String id) {
        Grievance grievance = grievanceRepository.findById(UUID.fromString(id)).orElse(null);
        if (grievance == null)
            return;
        grievance.setGrievance_number(serviceRequestId);
        grievance.setStatus(status);
        grievanceRepository.save(grievance);
    }
}
