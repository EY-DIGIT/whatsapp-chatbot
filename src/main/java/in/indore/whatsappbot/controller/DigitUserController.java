package in.indore.whatsappbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.indore.whatsappbot.service.DigitUserService;
import in.indore.whatsappbot.service.FormDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class DigitUserController {
    private static final Logger log = LoggerFactory.getLogger(DigitUserController.class);
    private final DigitUserService digitUserService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final FormDataService formDataService;

    public DigitUserController(DigitUserService digitUserService,
                               FormDataService formDataService) {
        this.digitUserService = digitUserService;
        this.formDataService = formDataService;
    }

    // Mirror Digit's OTP endpoint structure: POST /user-otp/v1/_send?tenantId=mp
    @PostMapping(path = "/user-otp/v1/_send", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendOtp(@RequestBody(required = false) JsonNode body,
                                          @RequestParam(value = "tenantId", required = false) String tenantId,
                                          @RequestParam(value = "transactionId", required = true) String transactionId) {

        try {
            ObjectNode node;
            if (body == null || body.isNull()) {
                node = mapper.createObjectNode();
            } else if (body.isObject()) {
                node = (ObjectNode) body;
            } else {
                node = mapper.createObjectNode();
                node.set("payload", body);
            }
            // ensure tenant in url is enforced by service; but include tenant in body if not present
            if (tenantId != null && !tenantId.isBlank()) {
                // if otp object exists, try to add tenantId inside it
                if (node.has("otp") && node.get("otp").isObject()) {
                    ((ObjectNode) node.get("otp")).put("tenantId", tenantId);
                }
            }
            return digitUserService.sendOtp(node, transactionId);
        } catch (IllegalStateException e){
            log.warn(e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()).toString());
        } catch (Exception e) {
            log.error("Error in sendOtp controller", e);
            return ResponseEntity
                    .status(409)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");

        }
    }

    // Mirror Digit's token endpoint: POST /user/oauth/token (form-urlencoded)
    @PostMapping(path = "/user/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> token(@RequestParam Map<String, String> form,
                                        @RequestParam(value = "transactionId", required = true) String transactionId) {
        try {
            String username = form.getOrDefault("username", "");
            String password = form.getOrDefault("password", "");
            String userType = form.getOrDefault("userType", null);
            String tenant = form.getOrDefault("tenantId", null);
           // return digitUserService.token(username, password, userType, tenant, transactionId);
            return digitUserService.validateOtp(username, password, userType, tenant, transactionId);
        } catch (IllegalStateException e){
            log.warn(e.getMessage());
            return ResponseEntity
                    .status(409)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("Error in token controller", e);
            return ResponseEntity.status(500).body("{\"error\":\"internal_error\",\"error_description\":\"Controller failed\"}");
        }
    }
}
