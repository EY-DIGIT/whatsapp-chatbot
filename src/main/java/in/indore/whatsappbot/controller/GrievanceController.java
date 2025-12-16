package in.indore.whatsappbot.controller;

import in.indore.whatsappbot.dto.GrievanceRequest;
import in.indore.whatsappbot.service.DigitGrievanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/grievance")
public class GrievanceController {

    private static final Logger log = LoggerFactory.getLogger(GrievanceController.class);

    private final DigitGrievanceService digitGrievanceService;

    public GrievanceController(DigitGrievanceService digitGrievanceService) {
        this.digitGrievanceService = digitGrievanceService;
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submitGrievance(@RequestBody GrievanceRequest request,
                                                  @RequestParam(value = "transactionId", required = true) String transactionId) {
        try {
            String srId = digitGrievanceService.createGrievance(request, transactionId);
            if (srId != null && !srId.isBlank()) {
                // Log serviceRequestId before returning
                log.info("Grievance created, serviceRequestId={}", srId);
                return ResponseEntity.ok("ok");
            } else {
                log.warn("Failed to create grievance for request service workflow");
                return ResponseEntity.status(500).body("failure");
            }
        } catch (IllegalStateException e) {
            log.error("Error while creating grievance", e);
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error while creating grievance", e);
            return ResponseEntity.status(500).body("failure");
        }
    }
}
