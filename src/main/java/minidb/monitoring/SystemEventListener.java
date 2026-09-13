package minidb.monitoring;

public interface SystemEventListener {

    void onEvent(SystemEvent event);
}