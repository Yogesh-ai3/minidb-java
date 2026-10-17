package minidb;

import minidb.monitoring.ConsoleMonitor;
import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;
import minidb.sql.Lexer;
import minidb.sql.Parser;
import minidb.sql.SqlCommand;
import minidb.sql.Token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class MiniDB {

    public static void main(String[] args) {
        SystemEventBus eventBus = new SystemEventBus();
        eventBus.addListener(new ConsoleMonitor());

        System.out.println("MiniDB starting...");
        System.out.println("Type SQL statements or 'exit' to quit.");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in))) {

            while (true) {
                System.out.print("MiniDB> ");

                String sql = reader.readLine();

                if (sql == null) {
                    break;
                }

                sql = sql.trim();

                if (sql.equalsIgnoreCase("exit")) {
                    break;
                }

                if (sql.isEmpty()) {
                    continue;
                }

                processCommand(sql, eventBus);
            }

        } catch (IOException exception) {
            System.err.println(
                    "Error reading terminal input: "
                            + exception.getMessage()
            );
        }

        System.out.println("Goodbye!");
    }

    private static void processCommand(
            String sql,
            SystemEventBus eventBus) {

        eventBus.publish(new SystemEvent(
                EventType.COMMAND_RECEIVED,
                "SQL command received"
        ));

        try {
            Lexer lexer = new Lexer(sql);
            List<Token> tokens = lexer.scanTokens();

            Parser parser = new Parser(tokens);
            SqlCommand command = parser.parse();

            eventBus.publish(new SystemEvent(
                    EventType.PARSING_SUCCEEDED,
                    "Parsed " + command.getClass().getSimpleName()
            ));

            System.out.println(
                    "Command parsed successfully: "
                            + command.getClass().getSimpleName()
            );
            System.out.println(
                    "Execution is not implemented yet."
            );

        } catch (RuntimeException exception) {
            eventBus.publish(new SystemEvent(
                    EventType.PARSING_FAILED,
                    exception.getMessage() == null
                            ? "Invalid SQL command"
                            : exception.getMessage()
            ));

            System.out.println(
                    "SQL error: " + exception.getMessage()
            );
        }
    }
}