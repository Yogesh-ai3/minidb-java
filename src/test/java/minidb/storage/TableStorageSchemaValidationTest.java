
package minidb.storage;

import minidb.buffer.BufferPool;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.StringValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableStorageSchemaValidationTest {

    @TempDir
    Path tempDir;

    private List<ColumnDefinition> studentColumns() {
        return List.of(
                new ColumnDefinition("id", DataType.INT, null, true),
                new ColumnDefinition("name", DataType.VARCHAR, 5, false),
                new ColumnDefinition("age", DataType.INT, null, false));
    }

    private TableStorage createStorage(DiskStorage diskStorage) {
        BufferPool bufferPool = new BufferPool(
                4, diskStorage, new minidb.monitoring.SystemEventBus());
        return new TableStorage(diskStorage, bufferPool);
    }

    @Test
    void acceptsValidRows() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("valid.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            tables.insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Maya"),
                    new IntegerValue(20)));

            assertEquals(1, tables.readAll("students").size());
        }
    }

    @Test
    void rejectsIncorrectValueCountWithoutInserting() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("count.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            assertThrows(IllegalArgumentException.class,
                    () -> tables.insert("students", List.of(
                            new IntegerValue(1),
                            new StringValue("Maya"))));

            assertTrue(tables.readAll("students").isEmpty());
        }
    }

    @Test
    void rejectsValuesWithIncorrectTypes() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("types.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            assertThrows(IllegalArgumentException.class,
                    () -> tables.insert("students", List.of(
                            new StringValue("one"),
                            new StringValue("Maya"),
                            new IntegerValue(20))));

            assertTrue(tables.readAll("students").isEmpty());
        }
    }

    @Test
    void rejectsStringsExceedingVarcharLength() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("length.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            assertThrows(IllegalArgumentException.class,
                    () -> tables.insert("students", List.of(
                            new IntegerValue(1),
                            new StringValue("Longer"),
                            new IntegerValue(20))));

            assertTrue(tables.readAll("students").isEmpty());
        }
    }

    @Test
    void acceptsStringAtVarcharLengthLimit() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("limit.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            tables.insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Alice"),
                    new IntegerValue(20)));

            assertEquals(1, tables.readAll("students").size());
        }
    }

    @Test
    void rejectsDuplicatePrimaryKeyWithoutInserting() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("duplicate-key.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            tables.insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Alice"),
                    new IntegerValue(20)));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> tables.insert("students", List.of(
                            new IntegerValue(1),
                            new StringValue("Bob"),
                            new IntegerValue(21))));

            assertTrue(exception.getMessage().contains("Duplicate primary key"));
            assertEquals(1, tables.readAll("students").size());
        }
    }

    @Test
    void acceptsDifferentPrimaryKeyValues() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("unique-keys.db"))) {
            TableStorage tables = createStorage(disk);
            tables.createTable("students", studentColumns());

            tables.insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Alice"),
                    new IntegerValue(20)));

            tables.insert("students", List.of(
                    new IntegerValue(2),
                    new StringValue("Bob"),
                    new IntegerValue(21)));

            assertEquals(2, tables.readAll("students").size());
        }
    }
}
