package in.indore.whatsappbot.service;

import in.indore.whatsappbot.enums.RequestFor;
import in.indore.whatsappbot.enums.RequestType;
import in.indore.whatsappbot.model.Grievance;
import in.indore.whatsappbot.model.UserRequests;
import in.indore.whatsappbot.model.ChatSession;
import in.indore.whatsappbot.model.ChatState;
import in.indore.whatsappbot.dto.OutgoingMessageDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class ChatFlowService {

    private final ConversationStateService stateService;
    private final TemplateService templateService;
    private final WhatsappMessageService waService;
    private final AuditService auditService;
    private final UserRequestService userRequestService;
    private final LanguageService languageService;
    private final FeedbackService feedbackService;
    private final DigitGrievanceService digitGrievanceService;
    private final GrievanceService grievanceService;
    private final FormDataService formDataService;

    public ChatFlowService(ConversationStateService stateService,
                           TemplateService templateService,
                           WhatsappMessageService waService,
                           AuditService auditService,
                           UserRequestService userRequestService,
                           LanguageService languageService,
                           FeedbackService feedbackService,
                           @Lazy DigitGrievanceService digitGrievanceService,
                           GrievanceService grievanceService,
                           FormDataService formDataService) {
        this.stateService = stateService;
        this.templateService = templateService;
        this.waService = waService;
        this.auditService = auditService;
        this.userRequestService = userRequestService;
        this.languageService = languageService;
        this.feedbackService = feedbackService;
        this.digitGrievanceService = digitGrievanceService;
        this.grievanceService = grievanceService;
        this.formDataService = formDataService;
    }

    @Value("${egov.form.host:https://urbanimcdev.eydemoapp.in/}")
    private String formHost;

    @Value("${egov.form.login.path:digit-ui/citizen/user/MobileLogin/}")
    private String loginFormPath;

    @Value("${egov.form.grievance.path:digit-ui/citizen/user/MobileLogin/}")
    private String grievanceFormPath;

    private String loginFromUrl;

    private String grievanceFormUrl;

    @PostConstruct
    public void init() {
        loginFromUrl = formHost + loginFormPath;
        grievanceFormUrl = formHost + grievanceFormPath;
    }

    public void handleIncoming(String phone, String text, String rawJson) {
        // audit incoming
        auditService.logIn(phone, text, rawJson);
        String lang = stateService.getLanguage(phone);
        ChatSession session = stateService.getSession(phone);
        if (session == null) session = new ChatSession();
        ChatState state = session.getState();

        // First-time language selection trigger
        if (lang == null && state != ChatState.LANG_SELECTION) {
            log.info("Language not set for {}, prompting language selection", phone);
            session.setState(ChatState.LANG_SELECTION);
            String out = templateService.t("prompt.choose_language", "en");
            send(phone, out);
            stateService.saveSession(phone, session);
            return;
        }

        // Use switch on state (null falls through to default)
        if (state == null) {
            // show menu by default
            log.info("No state for {}, starting at REQUEST_FOR", phone);
            session.setState(ChatState.REQUEST_FOR);
            String menu = templateService.t("prompt.request_for", lang);
            send(phone, menu);
            stateService.saveSession(phone, session);
            return;
        }

        switch (state) {
            case NEW_STATE, REGISTER_GRIEVANCE ->  handleNewState(phone, text, session);
            case LANG_SELECTION -> handleLanguageSelection(phone, text, session, lang);
            case REQUEST_FOR -> handleRequestFor(phone, text, session);
            case TRACK_GRIEVANCE -> handleTrackGrievance(phone, text, session);
            case MAIN_MENU -> handleMainMenu(phone, text, session);
            case FEEDBACK -> handleFeedback(phone, text, session);
            default -> {
                session.setState(ChatState.REQUEST_FOR);
                String out = templateService.t("prompt.request_for", lang);
                send(phone, out);
                stateService.saveSession(phone, session);
            }
        }
    }

    private void  handleNewState(String phone, String text, ChatSession session) {
        String ownerPhone = session.getRequestFor()==RequestFor.SELF ?
                null : userRequestService.getOwnerPhoneByRequestId(session.getRequestId());
        UserRequests ur = userRequestService.create(phone, session.getRequestFor(), session.isVerified(), ownerPhone);
        log.info("Handling new state for {}: {}", phone, text);
        session.setState(ChatState.MAIN_MENU);
        session.setRequestId(ur.getId());
        String lang = stateService.getLanguage(phone);
        String out = templateService.t("menu.main", lang);
        send(phone, out);
        stateService.saveSession(phone, session);
    }

    // --- per-state handlers ---
    private void handleLanguageSelection(String phone, String text, ChatSession session, String lang) {
        ChatState state = session.getRequestFor()==null?ChatState.REQUEST_FOR:ChatState.MAIN_MENU;
        String prompt = state == ChatState.REQUEST_FOR ? "prompt.request_for" : "menu.main";
        log.info("Handling language selection for {}: {}", phone, text);
        if (text.matches("(?i)^(1|english)$")) {
            stateService.setLanguage(phone, "en");
            languageService.saveLanguage(phone, "en");
            session.setState(state);
            String out = templateService.t(prompt, "en");
            send(phone, out);
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(2|hindi|हिंदी)$")) {
            stateService.setLanguage(phone, "hi");
            languageService.saveLanguage(phone, "hi");
            session.setState(state);
            String out = templateService.t(prompt, "hi");
            send(phone, out);
            stateService.saveSession(phone, session);
        } else {
            log.info("Invalid language selection for {}: {}", phone, text);
            String out = templateService.t("prompt.choose_language", lang == null ? "en" : lang);
            send(phone, out);
        }
    }

    private void handleRequestFor(String phone, String text, ChatSession session) {
        log.info("Handling request for {}: {}", phone, text);
        String lang = stateService.getLanguage(phone);
        if (text.matches("(?i)^(1|self)$")) {
            UserRequests ur = userRequestService.create(phone, RequestFor.SELF, true);
            session.setState(ChatState.MAIN_MENU);
            session.setRequestId(ur.getId());
            session.setRequestFor(RequestFor.SELF);
            session.setVerified(true);
            String out = templateService.t("menu.main", lang);
            send(phone, out);
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(2|other)$")) {
            UserRequests ur = userRequestService.create(phone, RequestFor.OTHER, false);
            session.setState(ChatState.REQUEST_FOR_OTHER);
            session.setRequestId(ur.getId());
            session.setRequestFor(RequestFor.OTHER);
            Map<String, Object> formData = Map.of(
                    "transactionId", ur.getId(),
                    "lang", lang
            );
            String formDataKey = formDataService.saveFormData(formData);
            String loginFormUrl = this.loginFromUrl+formDataKey;
            log.info("Generated grievance form URL for {}: {}", phone, loginFormUrl);
            String out = templateService.t("prompt.request_for_other", lang, loginFormUrl);
            send(phone, out);
            stateService.saveSession(phone, session);
        } else {
            log.info("Invalid request for option for {}: {}", phone, text);
            String out = templateService.t("prompt.request_for", lang);
            send(phone, out);
        }
    }

    private void handleMainMenu(String phone, String text, ChatSession session) {
        log.info("Handling main menu for {}: {}", phone, text);
        String lang = stateService.getLanguage(phone);
        if (text.matches("(?i)^(1)$")) {
            String citizenPhone;
            if (session.getRequestFor() == RequestFor.SELF) {
                citizenPhone = phone;
            } else if (session.isVerified()) {
                citizenPhone = userRequestService.getOwnerPhoneByRequestId(session.getRequestId());
            } else {
                log.info("User {} not verified to register grievances for OTHER", phone);
                String out = templateService.t("track.not_verified", lang);
                send(phone, out);
                handleRequestFor(phone, "2", session); // redirect to request for
                return;
            }
            if (citizenPhone != null && citizenPhone.length() == 12) {
                citizenPhone = citizenPhone.substring(2);
            }
            Grievance grievance = grievanceService.createGrievance(
                    new Grievance("NEW", session.getRequestId().toString()));
            log.info("Created new grievance with txn id {} for user {}", grievance.getId(), phone);

            Map<String, Object> formData = Map.of(
                    "transactionId", grievance.getId(),
                    "mobileNumber", citizenPhone,
                    "lang", lang
            );
            String formDataKey = formDataService.saveFormData(formData);
            String grievanceFormUrl = this.grievanceFormUrl+formDataKey;
            log.info("Generated grievance form URL for {}: {}", phone, grievanceFormUrl);
            String out = templateService.t("grievance.form", lang, grievanceFormUrl);
            send(phone, out);
            session.setState(ChatState.REGISTER_GRIEVANCE);
            userRequestService.userRequestsUpdate(session.getRequestId(), RequestType.REGISTER_GRIEVANCE);
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(2)$")) {
            session.setState(ChatState.TRACK_GRIEVANCE);
            // update DB state
            userRequestService.userRequestsUpdate(session.getRequestId(), RequestType.TRACK_GRIEVANCE);
            // Build and send interactive list of grievances (5 dummy items) in user's language
            try {
                String citizenPhone;
                if (session.getRequestFor() == RequestFor.SELF) {
                    citizenPhone = phone;
                } else if (session.isVerified()) {
                    citizenPhone = userRequestService.getOwnerPhoneByRequestId(session.getRequestId());
                } else {
                    log.info("User {} not verified to track grievances for OTHER", phone);
                    String out = templateService.t("track.not_verified", lang);
                    send(phone, out);
                    handleRequestFor(phone, "2", session); // redirect to request for
                    return;
                }
                if (citizenPhone != null && citizenPhone.length() == 12) {
                    citizenPhone = citizenPhone.substring(2);
                }
                String trackWait = templateService.t("track.wait", lang);
                send(phone, trackWait);
                List<String> grievanceList = digitGrievanceService.getGrievances(citizenPhone);
                if (grievanceList == null || grievanceList.isEmpty()) {
                    String noGrievanceMsg = templateService.t("grievances.not_found", lang, citizenPhone);
                    send(phone, noGrievanceMsg);
                    session.setState(ChatState.NEW_STATE);
                    stateService.saveSession(phone, session);
                    return;
                }
                if (grievanceList.size()>10) {
                    log.info("Grievance list for {} has {} items, showing first 10 with NEXT option", phone, grievanceList.size());
                    List<String> remainingGrievanceList = new ArrayList<>(grievanceList.subList(9, grievanceList.size()));
                    grievanceList = new ArrayList<>(grievanceList.subList(0, 9));
                    grievanceList.add("NEXT");
                    session.setGrievanceList(remainingGrievanceList);
                    stateService.saveSession(phone, session);
                }
                OutgoingMessageDto listMsg = waService.buildListMessage(phone, lang, grievanceList);
                auditService.logOut(phone, listMsg.text(), listMsg.rawJson());
                waService.sendMessage(listMsg);
            } catch (Exception e) {
                log.error("Failed to send interactive grievance list to {}", phone, e);
            }
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(3)$")) {
            session.setState(ChatState.FEEDBACK);
            userRequestService.userRequestsUpdate(session.getRequestId(), RequestType.FEEDBACK);
            String out = templateService.t("feedback.share", lang);
            send(phone, out);
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(4)$")) {
            session.setState(ChatState.LANG_SELECTION);
            userRequestService.userRequestsUpdate(session.getRequestId(), RequestType.CHANGE_LANGUAGE);
            String out = templateService.t("prompt.choose_language", lang);
            send(phone, out);
            stateService.saveSession(phone, session);
        } else if (text.matches("(?i)^(5)$")) {
            ChatSession newSession = new ChatSession();
            newSession.setState(ChatState.REQUEST_FOR);
            userRequestService.userRequestsUpdate(session.getRequestId(), RequestType.CHANGE_CITIZEN);
            String out = templateService.t("prompt.request_for", lang);
            send(phone, out);
            stateService.saveSession(phone, newSession);
        } else {
            log.info("Invalid main menu option for {}: {}", phone, text);
            String out = templateService.t("menu.main", lang);
            send(phone, out);
        }
    }

    private void handleTrackGrievance(String phone, String text, ChatSession session) {
        log.info("Handling track grievance selection for {}: {}", phone, text);
        String lang = stateService.getLanguage(phone);
        String serviceRequestId = text == null ? "" : text.trim();
        if (serviceRequestId.equals("NEXT")) {
            List<String> grievanceList = session.getGrievanceList();
            if (grievanceList == null || grievanceList.isEmpty()) {
                String noGrievanceMsg = templateService.t("grievances.not_found", lang, "");
                send(phone, noGrievanceMsg);
                session.setState(ChatState.NEW_STATE);
                stateService.saveSession(phone, session);
                return;
            }
            log.info("Handling NEXT for {}: {} grievances remaining", phone, grievanceList.size());
            if (grievanceList.size()>10) {
                List<String> remainingGrievanceList = new ArrayList<>(grievanceList.subList(9, grievanceList.size()));
                grievanceList = new ArrayList<>(grievanceList.subList(0, 9));
                grievanceList.add("NEXT");
                session.setGrievanceList(remainingGrievanceList);
                stateService.saveSession(phone, session);
            }
            OutgoingMessageDto listMsg = waService.buildListMessage(phone, lang, grievanceList);
            auditService.logOut(phone, listMsg.text(), listMsg.rawJson());
            waService.sendMessage(listMsg);
            return;
        }
        if (serviceRequestId.isEmpty()) {
            String notFoundMsg = templateService.t("track.not_found", lang, "");
            send(phone, notFoundMsg);
            session.setState(ChatState.NEW_STATE);
            stateService.saveSession(phone, session);
            log.info("Session set to new state for {} unable to get grievance details due to empty service request id", phone);
            return;
        }

        var details = digitGrievanceService.getGrievanceDetails(serviceRequestId);
        if (details == null) {
            String out = templateService.t("track.not_found", lang, serviceRequestId);
            send(phone, out);
            session.setState(ChatState.NEW_STATE);
            stateService.saveSession(phone, session);
            log.info("Session set to new state for {} unable to get grievance details due to invalid service request id", phone);
            return;
        }

        // format dates
        String createdStr = "-";
        String modifiedStr = "-";
        try {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy, hh:mm a", Locale.ENGLISH);
            if (details.createdTime() != null) {
                createdStr = java.time.Instant.ofEpochMilli(details.createdTime()).atZone(zone).format(fmt);
            }
            if (details.lastModifiedTime() != null) {
                modifiedStr = java.time.Instant.ofEpochMilli(details.lastModifiedTime()).atZone(zone).format(fmt);
            }
        } catch (Exception e) {
            session.setState(ChatState.NEW_STATE);
            stateService.saveSession(phone, session);
            log.warn("Failed to format date for grievance {}", serviceRequestId, e);
        }

        String desc = details.description() == null ? "" : details.description();
        String status = details.applicationStatus() == null ? "" : details.applicationStatus();
        String comments = details.comments() == null ? "" : details.comments();

        String out = templateService.t("track.details", lang,
                details.serviceRequestId(),
                status,
                desc,
                createdStr,
                modifiedStr,
                comments);

        send(phone, out);
        session.setState(ChatState.NEW_STATE);
        stateService.saveSession(phone, session);
        log.info("Session set to new state for {} after track grievance", phone);
    }

    private void handleFeedback(String phone, String text, ChatSession session) {
        log.info("Handling feedback for {}: {}", phone, text);
        if (!text.matches("^[1-5]$")) {
            String lang = stateService.getLanguage(phone);
            log.info("Invalid feedback input for {}: {}", phone, text);
            String out = templateService.t("feedback.invalid", lang);
            send(phone, out);
            return;
        }
        feedbackService.saveFeedback(session.getRequestId().toString(), Integer.valueOf(text));
        String lang = stateService.getLanguage(phone);
        String out = templateService.t("feedback.thanks", lang);
        send(phone, out);
        session.setState(ChatState.NEW_STATE);
        stateService.saveSession(phone, session);
        log.info("Session set to new state for {} after feedback", phone);
    }

    private void send(String phone, String body) {
        log.info("Sending message to {}:", phone);
        var msg = waService.buildTextMessage(phone, body);
        auditService.logOut(phone, body, msg.rawJson());
        waService.sendMessage(msg);
    }

    public void handleValidatedUser(String transactionId, String ownerPhone) {
        UserRequests userRequests = userRequestService.updateVerificationStatus(UUID.fromString(transactionId), ownerPhone, true);
        log.info("User {} verified successfully for request id {}", userRequests.getPhone(), transactionId);
        String lang = stateService.getLanguage(userRequests.getPhone());
        ChatSession session = stateService.getSession(userRequests.getPhone());
        session.setState(ChatState.MAIN_MENU);
        session.setVerified(true);
        String out = templateService.t("menu.main", lang);
        send(userRequests.getPhone(), out);
        stateService.saveSession(userRequests.getPhone(), session);
    }

    public void handleSessionExpire(String transactionId) {
        UserRequests userRequests = new UserRequests();
        log.info("User {} session expired for request id {}", userRequests.getPhone(), transactionId);
        String lang = stateService.getLanguage(userRequests.getPhone());
        ChatSession session =  new ChatSession();
        session.setState(ChatState.REQUEST_FOR);
        String out = templateService.t("prompt.request_for", lang);
        send(userRequests.getPhone(), out);
        stateService.saveSession(userRequests.getPhone(), session);
    }

    public void handleGrievanceRegistered(String serviceRequestId, Grievance grievance, String createdStr) {
        UserRequests userRequests = userRequestService.getById(UUID.fromString(grievance.getUserRequestId()));
        String phone = userRequests.getPhone();
        String lang = stateService.getLanguage(phone);
        String out = templateService.t("acknowledgement", lang, serviceRequestId, createdStr);
        send(userRequests.getPhone(), out);
        grievanceService.updateGrievance(serviceRequestId, "CREATED", String.valueOf(grievance.getId()));
        // return to main menu
        ChatSession session = stateService.getSession(phone);
        session.setState(ChatState.NEW_STATE);
        stateService.saveSession(phone, session);
        log.info("Session set to new state for {} after grievance registration", phone);
    }
}
