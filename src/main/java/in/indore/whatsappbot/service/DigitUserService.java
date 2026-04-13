package in.indore.whatsappbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.indore.whatsappbot.model.UserRequests;
import in.indore.whatsappbot.repository.UserRequestRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DigitUserService {
    private static final Logger log = LoggerFactory.getLogger(DigitUserService.class);

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${egov.user.service.host:https://urbanimcdev.eydemoapp.in/}")
    private String userHost;

    @Value("${egov.user.send-otp.path:user-otp/v1/_send}")
    private String otpPath;

    @Value("${egov.user.token.path:user/oauth/token}")
    private String tokenPath;
    
    @Value("${egov.otp.validate-otp.path:otp/v1/_validate}")
    private String otpValidatePath;


    @Value("${digit.tenantId:mp}")
    private String tenantId;

    @Value("${digit.token.basic:ZWdvdi11c2VyLWNsaWVudDo=}")
    private String oauthBasic;

    private final UserRequestRepository userRequestRepository;
    private final ChatFlowService chatFlowService;

    private String otpUrl;
    private String oauthUrl;
    private String validateOtpUrl;

    @PostConstruct
    public void init() {
        otpUrl = userHost + otpPath;
        oauthUrl = userHost + tokenPath;
        validateOtpUrl = userHost + otpValidatePath;
    }

    public DigitUserService(UserRequestRepository userRequestRepository,
                            ChatFlowService chatFlowService) {
        this.userRequestRepository = userRequestRepository;
        this.chatFlowService = chatFlowService;
    }

    /**
     * Call Digit OTP send API. requestJson is the JSON body to forward (should contain otp object).
     * If external returns 201 -> return ResponseEntity with 201 and created message. Otherwise forward external response body.
     */
    public ResponseEntity<String> sendOtp(ObjectNode requestJson, String transactionId) throws IllegalAccessException {

        UserRequests userRequests = userRequestRepository.findById(UUID.fromString(transactionId)).orElse(null);
        if (userRequests == null) {
            throw new IllegalStateException("Invalid transactionId: " + transactionId);
        }

        if ((Duration.between(
                userRequests.getVerificationRequestAt(),
                Instant.now()).toMinutes() > 15)) {
            throw new IllegalStateException("Verification request expired for transactionId: " + transactionId);
        }

        try {
            String url = otpUrl;
            if (!url.contains("?")) {
                url = url + "?tenantId=" + tenantId;
            } else if (!url.contains("tenantId=")) {
                url = url + "&tenantId=" + tenantId;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(requestJson), headers);
            try {
                ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
                if (resp.getStatusCode() == HttpStatus.CREATED) {
                    // return created message
                    ObjectNode out = mapper.createObjectNode();
                    out.put("message", "OTP created");
                    HttpHeaders outHeaders = new HttpHeaders();
                    outHeaders.setContentType(MediaType.APPLICATION_JSON);
                    return ResponseEntity.status(HttpStatus.CREATED).headers(outHeaders).body(mapper.writeValueAsString(out));
                }
                // forward external body and status
                String body = resp.getBody();
                HttpHeaders outHeaders = new HttpHeaders();
                MediaType ct = resp.getHeaders().getContentType();
                if (ct != null) outHeaders.setContentType(ct);
                return ResponseEntity.status(resp.getStatusCode()).headers(outHeaders).body(body == null ? "" : body);
            } catch (HttpClientErrorException httpEx) {
                // Forward the exact response body and status from remote (e.g., 400 with error JSON)
                String respBody = httpEx.getResponseBodyAsString();
                HttpStatus status = (HttpStatus) httpEx.getStatusCode();
                HttpHeaders outHeaders = new HttpHeaders();
                MediaType ct = httpEx.getResponseHeaders() == null ? null : httpEx.getResponseHeaders().getContentType();
                if (ct != null) outHeaders.setContentType(ct);
                log.warn("OTP API returned error {}: {}", status, respBody);
                return ResponseEntity.status(status).headers(outHeaders).body(respBody == null ? "" : respBody);
            }
        } catch (Exception e) {
            log.error("Error calling OTP API", e);
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "internal_error");
            err.put("message", "Failed to call OTP API");
            try {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapper.writeValueAsString(err));
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{}");
            }
        }
    }

    /**
     * Call Digit OAuth token API using form params. If response 200 -> return 200 with {"message":"User Verified"}
     * else forward external response body and status.
     */
    public ResponseEntity<String> token(String username, String password, String userType, String tenant, String transactionId) throws IllegalAccessException {
        UserRequests userRequests = userRequestRepository.findById(UUID.fromString(transactionId)).orElse(null);
        if (userRequests == null) {
            throw new IllegalStateException("Invalid transactionId: " + transactionId);
        }

        if (userRequests.isVerified())
            throw new IllegalStateException("User already verified for transactionId: " + transactionId);

        if ((Duration.between(
                userRequests.getVerificationRequestAt(),
                Instant.now()).toMinutes() > 15)) {
            throw new IllegalStateException("Verification request expired for transactionId: " + transactionId);
        }
        try {
            String url = oauthUrl;
            if (!url.contains("?")) {
                url = url + "?_=0"; // keep harmless param if needed
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            // always send our configured Authorization
            headers.set("Authorization", "Basic " + oauthBasic);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("username", username);
            body.add("password", password);
            body.add("tenantId", tenant == null ? tenantId : tenant);
            body.add("userType", userType == null ? "citizen" : userType);
            body.add("scope", "read");
            body.add("grant_type", "password");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            try {
                ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
                if (resp.getStatusCode() == HttpStatus.OK) {
                    ObjectNode out = mapper.createObjectNode();
                    out.put("message", "User Verified");
                    HttpHeaders outHeaders = new HttpHeaders();
                    outHeaders.setContentType(MediaType.APPLICATION_JSON);
                    chatFlowService.handleValidatedUser(transactionId, username);
                    return ResponseEntity.ok().headers(outHeaders).body(mapper.writeValueAsString(out));
                }
                String respBody = resp.getBody();
                HttpHeaders outHeaders = new HttpHeaders();
                MediaType ct = resp.getHeaders().getContentType();
                if (ct != null) outHeaders.setContentType(ct);
                return ResponseEntity.status(resp.getStatusCode()).headers(outHeaders).body(respBody == null ? "" : respBody);
            } catch (HttpClientErrorException httpEx) {
                String respBody = httpEx.getResponseBodyAsString();
                HttpStatus status = (HttpStatus) httpEx.getStatusCode();
                HttpHeaders outHeaders = new HttpHeaders();
                MediaType ct = httpEx.getResponseHeaders() == null ? null : httpEx.getResponseHeaders().getContentType();
                if (ct != null) outHeaders.setContentType(ct);
                log.warn("Token API returned error {}: {}", status, respBody);
                return ResponseEntity.status(status).headers(outHeaders).body(respBody == null ? "" : respBody);
            }
        } catch (Exception e) {
            log.error("Error calling token API", e);
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "internal_error");
            err.put("error_description", "Failed to call token API");
            try {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapper.writeValueAsString(err));
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{}");
            }
        }
    }
    
    public ResponseEntity<String> validateOtp(String identity, String otp, String userType,String tenant, String transactionId) {

        UserRequests userRequests = userRequestRepository.findById(UUID.fromString(transactionId)).orElse(null);
        if (userRequests == null) {
            throw new IllegalStateException("Invalid transactionId: " + transactionId);
        }

        if (userRequests.isVerified()) {
            throw new IllegalStateException("User already verified for transactionId: " + transactionId);
        }

        if ((Duration.between(userRequests.getVerificationRequestAt(), Instant.now()).toMinutes() > 15)) {
            throw new IllegalStateException("Verification request expired for transactionId: " + transactionId);
        }

        try {
            String url = validateOtpUrl;

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // Body
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode request = mapper.createObjectNode();

            ObjectNode requestInfo = mapper.createObjectNode();
            requestInfo.put("api_id", "1");
            requestInfo.put("ver", "1");
            requestInfo.putNull("ts");
            requestInfo.put("action", "create");
            requestInfo.put("did", "");
            requestInfo.put("key", "");
            requestInfo.put("msg_id", "");
            requestInfo.put("requester_id", "");
            requestInfo.putNull("auth_token");

            ObjectNode otpNode = mapper.createObjectNode();
            otpNode.put("tenantId", tenant);
            otpNode.put("identity", identity);
            otpNode.put("otp", otp);

            request.set("RequestInfo", requestInfo);
            request.set("otp", otpNode);

            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(request), headers);

            ResponseEntity<String> response = rest.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                ObjectNode out = mapper.createObjectNode();
                out.put("message", "User Verified");

                chatFlowService.handleValidatedUser(transactionId, identity);

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(out));
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (HttpClientErrorException ex) {
            log.warn("OTP Validation failed: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error calling OTP API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"internal_error\"}");
        }
    }
}
