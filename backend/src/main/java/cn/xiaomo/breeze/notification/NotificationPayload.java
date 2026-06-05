package cn.xiaomo.breeze.notification;

public record NotificationPayload(
    Long id,
    String type,
    String title,
    String body,
    String referenceType,
    Long referenceId,
    String createdAt
) {
    public static NotificationPayload from(Notification n) {
        return new NotificationPayload(
            n.getId(), n.getType(), n.getTitle(), n.getBody(),
            n.getReferenceType(), n.getReferenceId(),
            n.getCreatedAt() != null ? n.getCreatedAt().toString() : null
        );
    }
}
