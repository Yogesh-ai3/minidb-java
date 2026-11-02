
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TableStorageRestoreTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresSchemaAndRowsAfterReopeningDatabase() throws Exception {
        Path dataPath = tempDir.resolve("minidb.db");
        Path metadataPath = tempDir.resolve("tables.dat");

        List<ColumnDefinition> columns = List.of(
                new ColumnDefinition("id", DataType.INT, null, true),
                new ColumnDefinition("name", DataType.VARCHAR, 30, false)
        );

        // First session: create a table, insert a row, and flush its data.
        try (DiskStorage disk = new DiskStorage(dataPath)) {
            BufferPool pool = new BufferPool(4, disk, null);
            Catalog catalog = new Catalog();
            TableStorage storage =
                    new TableStorage(disk, pool, catalog, metadataPath);

            storage.createTable("students", columns);
            storage.insert("students", List.of(
                    new IntegerValue(1),
                    new StringValue("Alice")
            ));

            pool.flushAll();
        }

        // Second session: reconstruct the catalog and page mappings.
        try (DiskStorage disk = new DiskStorage(dataPath)) {
            BufferPool pool = new BufferPool(4, disk, null);
            Catalog catalog = new Catalog();
            TableStorage storage =
                    new TableStorage(disk, pool, catalog, metadataPath);

            storage.loadMetadata();

            assertTrue(catalog.tableExists("STUDENTS"));
            assertEquals(2, storage.getColumns("students").size());
            assertEquals(
                    List.of("students"),
                    catalog.getTableNames());

            assertEquals(
                    List.of(List.of(
                            new IntegerValue(1),
                            new StringValue("Alice")
                    )),
                    storage.readAll("students"));
        }
    }

    @Test
    void rejectsMetadataReferencingUnallocatedPages() throws Exception {
        Path dataPath = tempDir.resolve("minidb.db");
        Path metadataPath = tempDir.resolve("tables.dat");

        Catalog savedCatalog = new Catalog();
        savedCatalog.createTable("students", List.of(
                new ColumnDefinition("id", DataType.INT, null, true)
        ));

        // The database file has no allocated pages, but metadata references page 99.
        DatabaseMetadataStore.save(
                metadataPath,
                savedCatalog,
                Map.of("students", List.of(99L))
        );

        try (DiskStorage disk = new DiskStorage(dataPath)) {
            Catalog restoredCatalog = new Catalog();
            BufferPool pool = new BufferPool(4, disk, null);
            TableStorage storage = new TableStorage(
                    disk, pool, restoredCatalog, metadataPath);

            assertThrows(IOException.class, storage::loadMetadata);
            assertEquals(0, restoredCatalog.getTableCount());
        }
    }
}
