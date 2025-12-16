package in.indore.whatsappbot.dto;

public class GrievanceResponse {
    private boolean success;
    private String message;
    private String grievanceId;
    private String linkedMobile;        // Mobile number the grievance is linked to

    public GrievanceResponse() {}

    public GrievanceResponse(boolean success, String message, String grievanceId, String linkedMobile) {
        this.success = success;
        this.message = message;
        this.grievanceId = grievanceId;
        this.linkedMobile = linkedMobile;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getGrievanceId() { return grievanceId; }
    public void setGrievanceId(String grievanceId) { this.grievanceId = grievanceId; }

    public String getLinkedMobile() { return linkedMobile; }
    public void setLinkedMobile(String linkedMobile) { this.linkedMobile = linkedMobile; }
}
