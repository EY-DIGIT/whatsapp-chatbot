package in.indore.whatsappbot.model;


import java.io.Serializable;
import java.util.UUID;

public class ChatSession implements Serializable {
    private ChatState state;
    private UUID requestId;
    private in.indore.whatsappbot.enums.RequestFor requestFor;
    private boolean verified;

    public ChatSession() {}

    public ChatSession(ChatState state) { this.state = state; }

    public ChatState getState() { return state; }
    public void setState(ChatState state) { this.state = state; }

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public in.indore.whatsappbot.enums.RequestFor getRequestFor() { return requestFor; }
    public void setRequestFor(in.indore.whatsappbot.enums.RequestFor requestFor) { this.requestFor = requestFor; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
