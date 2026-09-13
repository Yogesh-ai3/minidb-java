package minidb.monitoring;

public class ConsoleMonitor implements SystemEventListener{
    @Override
    public void onEvent(SystemEvent event) {
        System.out.println(
                "[OS Monitor] "
                        + event.type()
                        + ": "
                        + event.message()
        );
    }
    
}
