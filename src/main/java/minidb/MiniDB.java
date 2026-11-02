package minidb;

import minidb.database.DatabaseManager;

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

import java.nio.file.Path;
import java.util.List;

public class MiniDB {

    public static void main(String[] args) {
    SystemEventBus eventBus = new SystemEventBus();
    eventBus.addListener(new ConsoleMonitor());

    try (DatabaseManager manager =
                 new DatabaseManager(Path.of("data"), eventBus)) {
        runRepl(manager, eventBus);
    } catch (IOException exception) {
        System.err.println("Database error: " + exception.getMessage());
    }

    System.out.println("Goodbye!");
}

    private static void runRepl(
            DatabaseManager manager,
            SystemEventBus eventBus
    ) {
        System.out.println("MiniDB starting...");
        System.out.println(
                "Type SQL statements or 'exit' to quit."
        );

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

                processCommand(sql, manager, eventBus);
            }

        } catch (IOException exception) {
            System.err.println(
                    "Error reading terminal input: "
                            + exception.getMessage()
            );
        }
    }

    private static void processCommand(
            String sql,
            DatabaseManager manager,
            SystemEventBus eventBus
    ) {
        eventBus.publish(new SystemEvent(
                EventType.COMMAND_RECEIVED,
                "SQL command received"
        ));

        final SqlCommand command;

        try {
            Lexer lexer = new Lexer(sql);
            List<Token> tokens = lexer.scanTokens();

            Parser parser = new Parser(tokens);
            command = parser.parse();

            eventBus.publish(new SystemEvent(
                    EventType.PARSING_SUCCEEDED,
                    "Parsed " + command.getClass().getSimpleName()
            ));

            System.out.println(
                    "Command parsed successfully: "
                            + command.getClass().getSimpleName()
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

            return;
        }

        try {
            String result = manager.execute(command);
            System.out.println(result);

        } catch (IOException | RuntimeException exception) {
            System.out.println(
                    "Execution error: " + exception.getMessage()
            );
        }
    }
}