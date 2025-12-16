package in.indore.whatsappbot.dto;

import java.util.List;
import java.util.Map;

public class GrievanceRequest {
    private String serviceCode;
    private String description;
    private Map<String, Object> additionalDetail;
    private String source;
    private Map<String, Object> address;

    private String mobileNumber;
    private String citizenName;
    private List<Map<String, Object>> verificationDocuments;

    public GrievanceRequest() {}

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getAdditionalDetail() { return additionalDetail; }
    public void setAdditionalDetail(Map<String, Object> additionalDetail) { this.additionalDetail = additionalDetail; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Map<String, Object> getAddress() { return address; }
    public void setAddress(Map<String, Object> address) { this.address = address; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getCitizenName() { return citizenName; }
    public void setCitizenName(String citizenName) { this.citizenName = citizenName; }

    public List<Map<String, Object>> getVerificationDocuments() { return verificationDocuments; }
    public void setVerificationDocuments(List<Map<String, Object>> verificationDocuments) { this.verificationDocuments = verificationDocuments; }
}
