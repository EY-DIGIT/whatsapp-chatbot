package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.UserRequests;
import in.indore.whatsappbot.enums.RequestFor;
import in.indore.whatsappbot.enums.RequestType;
import in.indore.whatsappbot.repository.UserRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserRequestService {
    private final UserRequestRepository repo;
    public UserRequestService(UserRequestRepository repo) { this.repo = repo; }

    @Transactional
    public UserRequests create(String phone, RequestFor requestFor, Boolean verified) {
        UserRequests ur = new UserRequests();
        ur.setPhone(phone);
        ur.setRequestFor(requestFor);
        ur.setVerified(verified);
        ur.setCreatedAt(Instant.now());
        if (requestFor == RequestFor.OTHER) {
            ur.setVerificationRequestAt(Instant.now());
        } else {
            ur.setVerificationRequestAt(null);
        }
        return repo.save(ur);
    }

    @Transactional
    public UserRequests create(String phone, RequestFor requestFor, Boolean verified, String ownerPhone) {
        UserRequests ur = new UserRequests();
        ur.setPhone(phone);
        ur.setRequestFor(requestFor);
        ur.setVerified(verified);
        ur.setCreatedAt(Instant.now());
        ur.setOwnerPhone(ownerPhone);
        if (requestFor == RequestFor.OTHER) {
            ur.setVerificationRequestAt(Instant.now());
        } else {
            ur.setVerificationRequestAt(null);
        }
        return repo.save(ur);
    }

    @Transactional
    public void userRequestsUpdate(UUID requestId, RequestType requestType) {
        UserRequests ur = repo.findById(requestId).orElseThrow();
        ur.setRequestType(requestType);
        repo.save(ur);
    }

    public String getOwnerPhoneByRequestId(UUID requestId) {
        return repo.findById(requestId)
                .map(UserRequests::getOwnerPhone)
                .orElse(null);
    }

    @Transactional
    public UserRequests updateVerificationStatus(UUID requestId, String ownerPhone, boolean isVerified) {
        UserRequests ur = repo.findById(requestId).orElseThrow();
        ur.setOwnerPhone(ownerPhone);
        ur.setVerified(isVerified);
        return repo.save(ur);
    }

    public UserRequests getById(UUID requestId) {
        return repo.findById(requestId).orElse(null);
    }

}

