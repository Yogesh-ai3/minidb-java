package minidb.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SystemEventBus {

    private final List<SystemEventListener> listeners = new ArrayList<>();

    public void addListener(SystemEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void publish(SystemEvent event) {
        Objects.requireNonNull(event, "event");

        for (SystemEventListener listener : List.copyOf(listeners)) {
            listener.onEvent(event);
        }
    }
}