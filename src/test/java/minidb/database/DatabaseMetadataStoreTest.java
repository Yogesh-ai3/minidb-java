
package minidb.database;

import minidb.catalog.Catalog;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMetadataStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsTableSchemaAndPageMappings() throws Exception {
        Catalog catalog = new Catalog();
        catalog.createTable("students", List.of(
                new ColumnDefinition("id", DataType.INT, null, true),
                new ColumnDefinition("name", DataType.VARCHAR, 40, false)
        ));

        Path metadataPath = tempDir.resolve("tables.dat");

        DatabaseMetadataStore.save(
                metadataPath,
                catalog,
                Map.of("students", List.of(2L, 5L))
        );

        List<DatabaseMetadataStore.TableMetadata> loaded =
                DatabaseMetadataStore.load(metadataPath);

        assertEquals(1, loaded.size());

        var table = loaded.get(0);
        assertEquals("students", table.tableName());
        assertEquals(List.of(2L, 5L), table.pageIds());
        assertEquals(2, table.columns().size());

        assertEquals(DataType.INT, table.columns().get(0).dataType());
        assertTrue(table.columns().get(0).primaryKey());

        assertEquals(DataType.VARCHAR, table.columns().get(1).dataType());
        assertEquals(40, table.columns().get(1).varcharLength());
        assertFalse(table.columns().get(1).primaryKey());
    }

    @Test
    void rejectsUnsupportedMetadataVersion() throws Exception {
        Path metadataPath = tempDir.resolve("tables.dat");

        try (DataOutputStream output =
                     new DataOutputStream(Files.newOutputStream(metadataPath))) {
            output.writeInt(0x4D444D44);
            output.writeInt(999);
            output.writeInt(0);
        }

        assertThrows(
                java.io.IOException.class,
                () -> DatabaseMetadataStore.load(metadataPath)
        );
    }

    @Test
    void rejectsMissingPageMappingWhenSaving() {
        Catalog catalog = new Catalog();
        catalog.createTable("students", List.of(
                new ColumnDefinition("id", DataType.INT, null, true)
        ));

        assertThrows(
                java.io.IOException.class,
                () -> DatabaseMetadataStore.save(
                        tempDir.resolve("tables.dat"),
                        catalog,
                        Map.of()
                )
        );
    }

    @Test
    void rejectsMetadataFileWithInvalidMagic() throws Exception {
        Path metadataPath = tempDir.resolve("tables.dat");

        try (DataOutputStream output =
                     new DataOutputStream(Files.newOutputStream(metadataPath))) {
            output.writeInt(12345);
            output.writeInt(1);
            output.writeInt(0);
        }

        assertThrows(
                java.io.IOException.class,
                () -> DatabaseMetadataStore.load(metadataPath)
        );
    }
}
