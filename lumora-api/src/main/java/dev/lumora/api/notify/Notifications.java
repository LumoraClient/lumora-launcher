package dev.lumora.api.notify;

/** Shows notifications in the Lumora style (top right, at most 4 at once, the rest wait). Thread-safe. */
public interface Notifications {
    void show(Notification notification);

    default void info(String title, String message) {
        show(Notification.builder(title).message(message).build());
    }

    default void success(String title, String message) {
        show(Notification.builder(title).message(message).type(NotificationType.SUCCESS).build());
    }

    default void warning(String title, String message) {
        show(Notification.builder(title).message(message).type(NotificationType.WARNING).build());
    }

    default void error(String title, String message) {
        show(Notification.builder(title).message(message).type(NotificationType.ERROR).build());
    }
}
