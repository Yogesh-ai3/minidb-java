package minidb.monitoring;

public record SystemEvent(
        EventType type,
        String message
) {
}