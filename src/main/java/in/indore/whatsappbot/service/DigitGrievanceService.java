package in.indore.whatsappbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.indore.whatsappbot.enums.RequestFor;
import in.indore.whatsappbot.model.Grievance;
import in.indore.whatsappbot.model.UserRequests;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class DigitGrievanceService {
    private static final Logger log = LoggerFactory.getLogger(DigitGrievanceService.class);

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ChatFlowService chatFlowService;
    private final GrievanceService grievanceService;
    private final UserRequestService userRequestService;

    @Value("${egov.user.service.host:https://urbanimcdev.eydemoapp.in/}")
    private String userHost;

    @Value("${egov.user.token.path:user/oauth/token}")
    private String tokenPath;

    @Value("${digit.token.basic:ZWdvdi11c2VyLWNsaWVudDo=}")
    private String tokenBasic;

    @Value("${digit.token.username:username}")
    private String tokenUsername;

    @Value("${digit.token.password:password}")
    private String tokenPassword;

    @Value("${digit.token.scope:read}")
    private String tokenScope;

    @Value("${digit.token.grant_type:password}")
    private String tokenGrantType;

    @Value("${digit.token.userType:EMPLOYEE}")
    private String tokenUserType;

    @Value("${digit.tenantId:mp.indore}")
    private String tenantId;

    @Value("${egov.pgr.service.host:https://urbanimcdev.eydemoapp.in/}")
    private String pgrHost;

    @Value("${egov.pgr.search.path:pgr-services/v2/request/_search}")
    private String pgrSearchPath;

    @Value("${egov.pgr.create.path:pgr-services/v2/request/_create}")
    private String pgrCreatePath;

    private String searchUrl;

    private String createUrl;

    private String tokenUrl;

    @PostConstruct
    public void init() {
        searchUrl = pgrHost + pgrSearchPath;
        createUrl = pgrHost + pgrCreatePath;
        tokenUrl = userHost + tokenPath;
    }


    public DigitGrievanceService(ChatFlowService chatFlowService,
                                 GrievanceService grievanceService,
                                 UserRequestService userRequestService){
        this.chatFlowService=chatFlowService;
        this.grievanceService=grievanceService;
        this.userRequestService=userRequestService;
    }
    /**
     * Fetch access token from Digit auth endpoint. Returns access_token string or null on failure.
     */
    private String fetchAccessToken() {
        try {
            log.info("Requesting access token from {}", tokenUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + tokenBasic);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("username", tokenUsername);
            body.add("scope", tokenScope);
            body.add("password", tokenPassword);
            body.add("grant_type", tokenGrantType);
            body.add("userType", tokenUserType);
            body.add("tenantId", tenantId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = rest.postForEntity(tokenUrl, request, String.class);
            if (resp.getStatusCode() != HttpStatus.OK) {
                log.warn("Token endpoint responded with status {}", resp.getStatusCode());
                return null;
            }
            String json = resp.getBody();
            if (json == null) {
                log.warn("Empty token response body");
                return null;
            }
            JsonNode node = mapper.readTree(json);
            JsonNode at = node.path("access_token");
            if (at.isMissingNode() || at.isNull()) {
                log.warn("access_token not present in token response: {}", json);
                return null;
            }
            String accessToken = at.asText();
            log.info("Obtained access token (masked) length={}", accessToken.length());
            return accessToken;
        } catch (Exception e) {
            log.error("Failed to fetch access token", e);
            return null;
        }
    }

    /**
     * Get grievances (serviceRequestId list) for given phone from Digit PGR API.
     * Returns empty list on error or if none found.
     */
    public List<String> getGrievances(String phone) {
        if (phone == null || phone.isBlank()) return Collections.emptyList();
        try {
            String token = fetchAccessToken();
            if (token == null) {
                log.warn("No access token available, aborting grievance fetch for {}", phone);
                return Collections.emptyList();
            }

            String url = searchUrl + "?tenantId=" + tenantId + "&mobileNumber=" + phone;
            log.info("Calling PGR search {} for phone={}", url, phone);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // build request { "RequestInfo": { "apiId": "Rainmaker", "authToken": "access_token" } }
            String requestJson = mapper.writeValueAsString(Collections.singletonMap("RequestInfo",
                    Collections.singletonMap("authToken", token)));
            // Ensure apiId also present
            JsonNode reqNode = mapper.readTree(requestJson);
            ((com.fasterxml.jackson.databind.node.ObjectNode) reqNode.path("RequestInfo")).put("apiId", "Rainmaker");
            String finalReq = mapper.writeValueAsString(reqNode);

            HttpEntity<String> entity = new HttpEntity<>(finalReq, headers);
            ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("PGR search returned {} for phone={}", resp.getStatusCode(), phone);
                return Collections.emptyList();
            }
            String body = resp.getBody();
            if (body == null || body.isBlank()) return Collections.emptyList();

            JsonNode root = mapper.readTree(body);
            JsonNode wrappers = root.path("ServiceWrappers");
            if (!wrappers.isArray() || wrappers.size() == 0) return Collections.emptyList();

            List<String> result = new ArrayList<>();
            for (JsonNode w : wrappers) {
                JsonNode service = w.path("service");
                JsonNode sr = service.path("serviceRequestId");
                if (!sr.isMissingNode() && !sr.isNull()) result.add(sr.asText());
            }
            log.info("Found {} grievances for {}", result.size(), phone);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch grievances for {}", phone, e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch full grievance details for a single serviceRequestId.
     * Returns GrievanceDetails or null on error/not found.
     */
    public in.indore.whatsappbot.dto.GrievanceDetails getGrievanceDetails(String serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId.isBlank()) return null;
        try {
            String token = fetchAccessToken();
            if (token == null) {
                log.warn("No access token available, aborting grievance detail fetch for {}", serviceRequestId);
                return null;
            }

            String url = searchUrl + "?tenantId=" + tenantId + "&serviceRequestId=" + java.net.URLEncoder.encode(serviceRequestId, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Calling PGR search {} for serviceRequestId={}", url, serviceRequestId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestJson = mapper.writeValueAsString(Collections.singletonMap("RequestInfo",
                    Collections.singletonMap("authToken", token)));
            JsonNode reqNode = mapper.readTree(requestJson);
            ((com.fasterxml.jackson.databind.node.ObjectNode) reqNode.path("RequestInfo")).put("apiId", "Rainmaker");
            String finalReq = mapper.writeValueAsString(reqNode);

            HttpEntity<String> entity = new HttpEntity<>(finalReq, headers);
            ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("PGR search returned {} for serviceRequestId={}", resp.getStatusCode(), serviceRequestId);
                return null;
            }
            String body = resp.getBody();
            if (body == null || body.isBlank()) return null;

            JsonNode root = mapper.readTree(body);
            JsonNode wrappers = root.path("ServiceWrappers");
            if (!wrappers.isArray() || wrappers.size() == 0) return null;

            JsonNode first = wrappers.get(0);
            JsonNode service = first.path("service");
            JsonNode workflow = first.path("workflow");

            String srId = service.path("serviceRequestId").asText(null);
            String status = service.path("applicationStatus").asText(null);
            String description = service.path("description").asText(null);
            long created = service.path("auditDetails").path("createdTime").asLong(0L);
            long lastModified = service.path("auditDetails").path("lastModifiedTime").asLong(0L);
            String comments = workflow.path("comments").asText(null);

            return new in.indore.whatsappbot.dto.GrievanceDetails(srId, status, description, created == 0 ? null : created, lastModified == 0 ? null : lastModified, comments);
        } catch (Exception e) {
            log.error("Failed to fetch grievance details for {}", serviceRequestId, e);
            return null;
        }
    }

    /**
     * Create a grievance in PGR using the configured create endpoint.
     * This version accepts the structured GrievanceRequest DTO (serviceCode, description, additionalDetail,
     * source, address, mobileNumber, citizenName, verificationDocuments) and maps it into the
     * PGR create JSON payload while enforcing static fields like tenantId, source, citizen.type, roles and workflow.action.
     */
    public String createGrievance(in.indore.whatsappbot.dto.GrievanceRequest req, String transactionId) {
        Grievance grievance = grievanceService.getGrievanceById(transactionId);
        if (grievance == null) {
            log.warn("No grievance found for transactionId={}", transactionId);
            throw new IllegalStateException("Invalid transactionId: " + transactionId);
        }
        UserRequests userRequests = userRequestService.getById(UUID.fromString(grievance.getUserRequestId()));
        validateRequest(grievance, userRequests, req.getMobileNumber());
        if (req == null) return null;
        try {
            ObjectNode root = mapper.createObjectNode();

            // Build service node
            ObjectNode service = mapper.createObjectNode();
            service.put("tenantId", tenantId); // enforce configured tenant
            service.put("serviceCode", req.getServiceCode() == null ? "" : req.getServiceCode());
            service.put("description", req.getDescription() == null ? "" : req.getDescription());
            // additionalDetail map if provided
            if (req.getAdditionalDetail() != null) {
                JsonNode add = mapper.convertValue(req.getAdditionalDetail(), JsonNode.class);
                service.set("additionalDetail", add);
            } else {
                service.set("additionalDetail", mapper.createObjectNode());
            }
            // source - enforce whatsapp
            service.put("source", "whatsapp");

            // address - take as-is from DTO if provided
            if (req.getAddress() != null) {
                JsonNode addr = mapper.convertValue(req.getAddress(), JsonNode.class);
                service.set("address", addr);
            } else {
                service.set("address", mapper.createObjectNode());
            }

            // citizen
            ObjectNode citizen = mapper.createObjectNode();
            citizen.put("name", req.getCitizenName() == null ? "" : req.getCitizenName());
            citizen.put("type", "CITIZEN"); // enforce
            citizen.put("mobileNumber", req.getMobileNumber() == null ? "" : req.getMobileNumber());
            ArrayNode roles = mapper.createArrayNode();
            ObjectNode role = mapper.createObjectNode();
            role.putNull("id");
            role.put("name", "Citizen");
            role.put("code", "CITIZEN");
            role.put("tenantId", tenantId);
            roles.add(role);
            citizen.set("roles", roles);
            citizen.put("tenantId", tenantId);
            service.set("citizen", citizen);

            root.set("service", service);

            // workflow
            ObjectNode workflow = mapper.createObjectNode();
            workflow.put("action", "APPLY"); // enforce
            if (req.getVerificationDocuments() != null && !req.getVerificationDocuments().isEmpty()) {
                ArrayNode vdocs = mapper.createArrayNode();
                for (Map<String, Object> doc : req.getVerificationDocuments()) {
                    JsonNode d = mapper.convertValue(doc, JsonNode.class);
                    // ensure required fields exist (documentType, fileStoreId)
                    ObjectNode dnode = d.isObject() ? (ObjectNode) d : mapper.createObjectNode();
                    if (!dnode.has("documentType")) dnode.put("documentType", "PHOTO");
                    if (!dnode.has("fileStoreId")) dnode.put("fileStoreId", "");
                    if (!dnode.has("additionalDetails")) dnode.set("additionalDetails", mapper.createObjectNode());
                    vdocs.add(dnode);
                }
                workflow.set("verificationDocuments", vdocs);
            }
            root.set("workflow", workflow);

            // Delegate to existing JsonNode-based sender which will add RequestInfo and POST
            return createGrievance((JsonNode) root, grievance);
        } catch (IllegalStateException e) {
            log.error("Failed to create grievance from structured DTO", e);
            throw new IllegalStateException(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create grievance from structured DTO", e);
            return null;
        }
    }

    /**
     * Create a grievance using a raw JSON payload from the caller.
     * This method preserves fields present in the request (address, geoLocation, verificationDocuments etc.)
     * but enforces fixed values for tenantId, source, citizen.type, citizen.roles and workflow.action.
     * Returns serviceRequestId if created successfully, otherwise null.
     */
    public String createGrievance(com.fasterxml.jackson.databind.JsonNode incoming, Grievance grievance) {
        if (incoming == null || !incoming.isObject()) return null;
        try {
            String token = fetchAccessToken();
            if (token == null) {
                log.warn("No access token available, aborting grievance create");
                return null;
            }

            // Make a mutable copy of incoming root
            ObjectNode root = incoming.deepCopy();

            // Ensure service object exists and preserve incoming address/geolocation as-is
            ObjectNode service = null;
            if (root.has("service") && root.path("service").isObject()) {
                service = (ObjectNode) root.path("service");
            } else {
                service = mapper.createObjectNode();
                root.set("service", service);
            }

            // Enforce static/fixed values
            service.put("tenantId", tenantId);
            service.put("source", "whatsapp");

            // Citizen object: preserve name and mobileNumber if present; enforce type and roles
            ObjectNode citizen;
            if (service.has("citizen") && service.path("citizen").isObject()) {
                citizen = (ObjectNode) service.path("citizen");
            } else {
                citizen = mapper.createObjectNode();
                service.set("citizen", citizen);
            }
            // preserve name and mobileNumber if set in incoming; otherwise keep as empty strings
            String name = citizen.path("name").asText("");
            String mobile = citizen.path("mobileNumber").asText("");
            citizen.removeAll(); // clear and re-add to enforce fields order
            citizen.put("name", name);
            citizen.put("type", "CITIZEN");
            citizen.put("mobileNumber", mobile);
            // roles array - set fixed role
            ArrayNode roles = mapper.createArrayNode();
            ObjectNode role = mapper.createObjectNode();
            role.putNull("id");
            role.put("name", "Citizen");
            role.put("code", "CITIZEN");
            role.put("tenantId", tenantId);
            roles.add(role);
            citizen.set("roles", roles);
            citizen.put("tenantId", tenantId);

            // Ensure workflow exists and enforce action
            ObjectNode workflow;
            if (root.has("workflow") && root.path("workflow").isObject()) {
                workflow = (ObjectNode) root.path("workflow");
            } else {
                workflow = mapper.createObjectNode();
                root.set("workflow", workflow);
            }
            workflow.put("action", "APPLY");

            // Add RequestInfo with token
            ObjectNode reqInfo = mapper.createObjectNode();
            reqInfo.put("apiId", "Rainmaker");
            reqInfo.put("authToken", token);
            root.set("RequestInfo", reqInfo);

            String payload = mapper.writeValueAsString(root);

            String url = createUrl + "?tenantId=" + tenantId;
            log.info("Calling PGR create {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("PGR create returned {}", resp.getStatusCode());
                return null;
            }
            String body = resp.getBody();
            if (body == null || body.isBlank()) return null;

            JsonNode rootResp = mapper.readTree(body);
            JsonNode wrappers = rootResp.path("ServiceWrappers");
            if (!wrappers.isArray() || wrappers.size() == 0) return null;
            JsonNode first = wrappers.get(0);
            JsonNode serviceResp = first.path("service");
            String srId = serviceResp.path("serviceRequestId").asText(null);
            if (srId != null) {
                log.info("Created grievance with serviceRequestId={}", srId);
                chatFlowService.handleGrievanceRegistered(srId, grievance);
                return srId;
            }
            log.warn("PGR create response missing serviceRequestId: {}", body);
            return null;
        } catch (Exception e) {
            log.error("Failed to create grievance from raw JSON", e);
            return null;
        }
    }

    private void validateRequest(Grievance grievance, UserRequests userRequests, String mobileNumber) {
        if ("CREATED".equals(grievance.getStatus())){
            log.warn("Grievance already created for transactionId={}", grievance.getId());
            throw new IllegalStateException("Grievance already created for transactionId: " + grievance.getId());
        }
        if ((Duration.between(
                grievance.getCreatedAt(),
                Instant.now()).toMinutes() > 15)) {
            throw new IllegalStateException("Grievance submission request expired for transactionId: " + grievance.getId());
        }
        if (userRequests.getRequestFor().equals(RequestFor.OTHER)){
            if (!userRequests.isVerified()){
                log.warn("User request not verified for transactionId={}", grievance.getId());
                throw new IllegalStateException("User request not verified for transactionId: " + grievance.getId());
            }
            if (!Objects.equals(userRequests.getOwnerPhone(), mobileNumber)){
                log.warn("Mobile number does not match verified phone for transactionId={}", grievance.getId());
                throw new IllegalStateException("Mobile number does not match verified phone for transactionId: " + grievance.getId());
            }
        }
    }
}
