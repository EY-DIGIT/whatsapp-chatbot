package in.indore.whatsappbot.dto;

public record GrievanceDetails(
        String serviceRequestId,
        String applicationStatus,
        String description,
        Long createdTime,
        Long lastModifiedTime,
        String comments
) {}

