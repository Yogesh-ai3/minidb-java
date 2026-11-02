
package minidb.execution;

import minidb.buffer.BufferPool;
import minidb.sql.Lexer;
import minidb.sql.Parser;
import minidb.sql.Token;
import minidb.storage.DiskStorage;
import minidb.storage.TableStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlConstraintIntegrationTest {

    @TempDir
    Path tempDir;

    private String runSql(SqlExecutionEngine engine, String sql)
            throws Exception {
        List<Token> tokens = new Lexer(sql).scanTokens();
        return engine.execute(new Parser(tokens).parse());
    }

    @Test
    void enforcesSchemaAndPrimaryKeyThroughSql() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("integration.db"))) {

            BufferPool bufferPool = new BufferPool(4, disk,null);
            TableStorage storage = new TableStorage(disk, bufferPool);
            SqlExecutionEngine engine = new SqlExecutionEngine(storage);

            runSql(engine,
                    "CREATE TABLE students "
                    + "(id INT PRIMARY KEY, name VARCHAR(5), age INT)");

            runSql(engine,
                    "INSERT INTO students VALUES (1, 'Alice', 20)");

            // Duplicate primary key must be rejected.
            Exception duplicate = assertThrows(
                    IllegalArgumentException.class,
                    () -> runSql(engine,
                            "INSERT INTO students VALUES (1, 'Bob', 21)")
            );
            assertTrue(duplicate.getMessage()
                    .contains("Duplicate primary key"));

            // Wrong type must be rejected.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> runSql(engine,
                            "INSERT INTO students VALUES ('two', 'Bob', 21)")
            );

            // VARCHAR length must be enforced.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> runSql(engine,
                            "INSERT INTO students VALUES (2, 'Longer', 21)")
            );

            // Valid distinct key must still be accepted.
            runSql(engine,
                    "INSERT INTO students VALUES (2, 'Bob', 21)");

            String result = runSql(engine,
                    "SELECT * FROM students");

            assertTrue(result.contains("Alice"));
            assertTrue(result.contains("Bob"));
            assertTrue(result.contains("(2 rows)"));
        }
    }
}
