package minidb.storage;

import minidb.buffer.BufferPool;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTableAndReadsInsertedRecords() throws Exception {
        Path file = tempDir.resolve("minidb.db");

        try (DiskStorage disk = new DiskStorage(file)) {
            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);

            tables.createTable("students");

            List<SqlValue> alice = List.of(
                    new IntegerValue(1),
                    new StringValue("Alice")
            );

            List<SqlValue> bob = List.of(
                    new IntegerValue(2),
                    new StringValue("Bob")
            );

            tables.insert("students", alice);
            tables.insert("students", bob);

            assertEquals(
                    List.of(alice, bob),
                    tables.readAll("STUDENTS")
            );
        }
    }

    @Test
    void allocatesAnotherPageWhenCurrentPageIsFull()
            throws Exception {
        Path file = tempDir.resolve("minidb.db");

        try (DiskStorage disk = new DiskStorage(file)) {
            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);

            tables.createTable("logs");

            String payload = "x".repeat(3000);

            tables.insert(
                    "logs",
                    List.of(new StringValue(payload))
            );

            tables.insert(
                    "logs",
                    List.of(new StringValue(payload))
            );

            assertEquals(2, tables.getTablePageIds("logs").size());
            assertEquals(2, tables.readAll("logs").size());
        }
    }

    @Test
    void rejectsDuplicateTableNamesIgnoringCase()
            throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("minidb.db"))) {

            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);

            tables.createTable("students");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> tables.createTable("STUDENTS")
            );
        }
    }

    @Test
    void rejectsInsertIntoUnknownTable() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("minidb.db"))) {

            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> tables.insert(
                            "missing",
                            List.of(new IntegerValue(1))
                    )
            );
        }
    }

    @Test
    void recordsSurviveDiskStorageReopen() throws Exception {
        Path file = tempDir.resolve("minidb.db");
        long pageId;

        List<SqlValue> row = List.of(
                new IntegerValue(7),
                new StringValue("persisted")
        );

        try (DiskStorage disk = new DiskStorage(file)) {
            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);

            tables.createTable("records");
            tables.insert("records", row);

            pageId = tables.getTablePageIds("records").get(0);
            pool.flushAll();
        }

        // Reopen the database file and read the persisted page directly.
        try (DiskStorage reopened = new DiskStorage(file)) {
            Page page = reopened.readPage(pageId);

            assertEquals(
                    List.of(row),
                    RecordPage.readRecords(page.getData())
            );
        }
    }
}