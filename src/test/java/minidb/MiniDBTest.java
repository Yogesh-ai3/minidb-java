package minidb;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniDBTest {

    @Test
    void cliContinuesAfterInvalidSql() throws Exception {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String commands = String.join(
                System.lineSeparator(),
                "CREATE DATABASE college;",
                "SELECT FROM;",
                "USE college;",
                "exit",
                ""
        );

        try {
            System.setIn(new ByteArrayInputStream(
                    commands.getBytes(StandardCharsets.UTF_8)
            ));

            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            MiniDB.main(new String[0]);

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("PARSING_SUCCEEDED"));
        assertTrue(result.contains("PARSING_FAILED"));
        assertTrue(result.contains("CreateDatabase"));
        assertTrue(result.contains("UseDatabase"));
        assertTrue(result.contains("SQL error:"));
        assertTrue(result.contains("Goodbye!"));
    }

    @Test
    void cliExitsCleanlyWhenInputEnds() throws Exception {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));

            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            MiniDB.main(new String[0]);

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        assertTrue(
                output.toString(StandardCharsets.UTF_8)
                        .contains("Goodbye!")
        );
    }
}