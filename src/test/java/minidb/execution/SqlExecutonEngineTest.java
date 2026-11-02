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

class SqlExecutionEngineTest {

    @TempDir
    Path tempDir;

    private static String runSql(
            SqlExecutionEngine engine,
            String sql
    ) throws Exception {
        List<Token> tokens = new Lexer(sql).scanTokens();
        return engine.execute(new Parser(tokens).parse());
    }

    @Test
    void executesCreateInsertAndSelectSql() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("engine.db"))) {

            BufferPool pool = new BufferPool(4, disk, null);
            TableStorage tables = new TableStorage(disk, pool);
            SqlExecutionEngine engine = new SqlExecutionEngine(tables);

            assertTrue(runSql(
                    engine,
                    "CREATE TABLE students "
                            + "(id INT PRIMARY KEY, "
                            + "name VARCHAR(50), age INT);"
            ).contains("created successfully"));

            assertTrue(runSql(
                    engine,
                    "INSERT INTO students VALUES (1, 'Alice', 20);"
            ).contains("1 row inserted"));

            assertTrue(runSql(
                    engine,
                    "INSERT INTO students VALUES (2, 'Bob', 16);"
            ).contains("1 row inserted"));

            String allRows = runSql(
                    engine,
                    "SELECT * FROM students;"
            );

            assertTrue(allRows.contains("Alice"));
            assertTrue(allRows.contains("Bob"));
            assertTrue(allRows.contains("(2 rows)"));

            String filtered = runSql(
                    engine,
                    "SELECT name FROM students WHERE age > 18;"
            );

            assertTrue(filtered.contains("Alice"));
            assertFalse(filtered.contains("Bob"));
            assertTrue(filtered.contains("(1 rows)"));
        }
    }

    @Test
    void reportsUnsupportedExecutionClearly() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("unsupported.db"))) {

            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);
            SqlExecutionEngine engine = new SqlExecutionEngine(tables);

            var command = new Parser(
                    new Lexer("CREATE DATABASE college;").scanTokens()
            ).parse();

            UnsupportedOperationException exception = assertThrows(
                    UnsupportedOperationException.class,
                    () -> engine.execute(command)
            );

            assertTrue(
                    exception.getMessage().contains("CreateDatabase")
            );
        }
    }
}