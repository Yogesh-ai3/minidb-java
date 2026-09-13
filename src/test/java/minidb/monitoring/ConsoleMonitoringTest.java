package minidb.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ConsoleMonitoringTest {

    @Test
    void shouldAcceptSystemEvent() {

        ConsoleMonitor monitor = new ConsoleMonitor();

        SystemEvent event = new SystemEvent(
                EventType.PROCESS_STARTED,
                "INSERT operation started"
        );

        assertDoesNotThrow(() -> monitor.onEvent(event));
    }
    
}
