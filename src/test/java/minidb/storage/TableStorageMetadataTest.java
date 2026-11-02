
package minidb.storage;

import minidb.buffer.BufferPool;
import minidb.catalog.Catalog;
import minidb.database.DatabaseMetadataStore;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.StringValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableStorageMetadataTest {

    @TempDir
    Path tempDir;

    @Test
    void tableCreationPersistsSchemaAndPageMapping() throws Exception {
        Path dataPath = tempDir.resolve("minidb.db");
        Path metadataPath = tempDir.resolve("tables.dat");

        Catalog catalog = new Catalog();

        try (DiskStorage disk = new DiskStorage(dataPath)) {
            BufferPool pool = new BufferPool(4, disk, null);
            TableStorage storage =
                    new TableStorage(disk, pool, catalog, metadataPath);

            storage.createTable("students", List.of(
                    new ColumnDefinition("id", DataType.INT, null, true),
                    new ColumnDefinition("name", DataType.VARCHAR, 30, false)
            ));

            assertTrue(java.nio.file.Files.exists(metadataPath));

            var loaded = DatabaseMetadataStore.load(metadataPath);
            assertEquals(1, loaded.size());
            assertEquals("students", loaded.get(0).tableName());
            assertEquals(2, loaded.get(0).columns().size());
            assertEquals(
                    storage.getTablePageIds("students"),
                    loaded.get(0).pageIds());

            pool.flushAll();
        }
    }

    @Test
    void allocatingAnotherDataPageUpdatesMetadata() throws Exception {
        Path dataPath = tempDir.resolve("minidb.db");
        Path metadataPath = tempDir.resolve("tables.dat");

        Catalog catalog = new Catalog();

        try (DiskStorage disk = new DiskStorage(dataPath)) {
            BufferPool pool = new BufferPool(4, disk, null);
            TableStorage storage =
                    new TableStorage(disk, pool, catalog, metadataPath);

            storage.createTable("notes", List.of(
                    new ColumnDefinition("id", DataType.INT, null, true),
                    new ColumnDefinition("note", DataType.VARCHAR, 3000, false)
            ));

            String note = "x".repeat(2500);

            storage.insert("notes", List.of(
                    new IntegerValue(1),
                    new StringValue(note)
            ));

            storage.insert("notes", List.of(
                    new IntegerValue(2),
                    new StringValue(note)
            ));

            var loaded = DatabaseMetadataStore.load(metadataPath);

            assertEquals(
                    2,
                    loaded.get(0).pageIds().size(),
                    "Metadata should include the newly allocated data page");

            assertEquals(
                    2,
                    storage.readAll("notes").size());

            pool.flushAll();
        }
    }

    @Test
    void failedDuplicateTableCreationDoesNotReplaceMetadata() throws Exception {
        Path dataPath = tempDir.resolve("minidb.db");
        Path metadataPath = tempDir.resolve("tables.dat");

        Catalog catalog = new Catalog();

        try (DiskStorage disk = new DiskStorage(dataPath)) {
            BufferPool pool = new BufferPool(4, disk, null);
            TableStorage storage =
                    new TableStorage(disk, pool, catalog, metadataPath);

            storage.createTable("students", List.of(
                    new ColumnDefinition("id", DataType.INT, null, true)
            ));

            byte[] originalMetadata = java.nio.file.Files.readAllBytes(metadataPath);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> storage.createTable("STUDENTS", List.of(
                            new ColumnDefinition("other", DataType.INT, null, false)
                    ))
            );

            assertArrayEquals(
                    originalMetadata,
                    java.nio.file.Files.readAllBytes(metadataPath));

            pool.flushAll();
        }
    }
}
