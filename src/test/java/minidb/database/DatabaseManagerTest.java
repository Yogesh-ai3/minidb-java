
package minidb.database;

import minidb.monitoring.SystemEventBus;
import minidb.sql.SqlCommand;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.StringValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void databaseAndRowsSurviveRestart() throws Exception {
        try (DatabaseManager manager =
                     new DatabaseManager(tempDir, new SystemEventBus())) {

            manager.execute(new SqlCommand.CreateDatabase("college"));
            manager.execute(new SqlCommand.UseDatabase("college"));

            manager.execute(new SqlCommand.CreateTable("students", List.of(
                    new ColumnDefinition("id", DataType.INT, null, true),
                    new ColumnDefinition("name", DataType.VARCHAR, 30, false)
            )));

            manager.execute(new SqlCommand.Insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Alice")
            )));
        }

        try (DatabaseManager manager =
                     new DatabaseManager(tempDir, new SystemEventBus())) {

            assertNull(manager.getCurrentDatabaseName());

            manager.execute(new SqlCommand.UseDatabase("college"));

            String result = manager.execute(new SqlCommand.Select(
                    true, List.of(), "students", Optional.empty()));

            assertTrue(result.contains("Alice"), result);
            assertTrue(result.contains("1 rows"), result);
        }
    }

    @Test
    void startsWithoutASelectedDatabase() throws Exception {
        try (DatabaseManager manager =
                     new DatabaseManager(tempDir, new SystemEventBus())) {

            assertNull(manager.getCurrentDatabaseName());

            assertThrows(
                    IllegalStateException.class,
                    () -> manager.execute(new SqlCommand.CreateTable(
                            "students",
                            List.of(new ColumnDefinition(
                                    "id", DataType.INT, null, true))
                    ))
            );
        }
    }

    @Test
    void rejectsUsingANonexistentDatabase() throws Exception {
        try (DatabaseManager manager =
                     new DatabaseManager(tempDir, new SystemEventBus())) {

            assertThrows(
                    IOException.class,
                    () -> manager.execute(
                            new SqlCommand.UseDatabase("missing"))
            );

            assertNull(manager.getCurrentDatabaseName());
        }
    }

    @Test
    void rejectsCreatingAnExistingDatabase() throws Exception {
        try (DatabaseManager manager =
                     new DatabaseManager(tempDir, new SystemEventBus())) {

            manager.execute(new SqlCommand.CreateDatabase("college"));

            assertThrows(
                    IOException.class,
                    () -> manager.execute(
                            new SqlCommand.CreateDatabase("COLLEGE"))
            );
        }
    }

    
}
