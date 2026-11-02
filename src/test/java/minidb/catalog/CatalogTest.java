package minidb.catalog;

import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {

    private List<ColumnDefinition> studentColumns() {
        return List.of(
                new ColumnDefinition("id", DataType.INT, null, true),
                new ColumnDefinition("name", DataType.VARCHAR, 50, false),
                new ColumnDefinition("age", DataType.INT, null, false)
        );
    }

    @Test
    void createsAndRetrievesTableSchema() {
        Catalog catalog = new Catalog();

        catalog.createTable("students", studentColumns());

        TableSchema schema = catalog.getTable("students");

        assertEquals("students", schema.getTableName());
        assertEquals(3, schema.getColumns().size());
        assertEquals(1, schema.getColumnIndex("name"));
        assertEquals(DataType.VARCHAR, schema.getColumn("name").dataType());
        assertEquals(50, schema.getColumn("name").varcharLength());
        assertTrue(schema.getColumn("id").primaryKey());
    }

    @Test
    void rejectsDuplicateTableNames() {
        Catalog catalog = new Catalog();

        catalog.createTable("students", studentColumns());

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.createTable("students", studentColumns())
        );
    }

    @Test
    void rejectsUnknownTablesAndColumns() {
        Catalog catalog = new Catalog();
        catalog.createTable("students", studentColumns());

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.getTable("teachers")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.getTable("students").getColumnIndex("salary")
        );
    }

    @Test
    void rejectsDuplicateColumnNames() {
        Catalog catalog = new Catalog();

        List<ColumnDefinition> columns = List.of(
                new ColumnDefinition("id", DataType.INT, null, true),
                new ColumnDefinition("id", DataType.INT, null, false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.createTable("students", columns)
        );
    }

    @Test
    void rejectsTablesWithoutColumns() {
        Catalog catalog = new Catalog();

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.createTable("empty_table", List.of())
        );
    }

    @Test
    void reportsTableNamesAndCount() {
        Catalog catalog = new Catalog();

        catalog.createTable("students", studentColumns());
        catalog.createTable(
                "courses",
                List.of(new ColumnDefinition("id", DataType.INT, null, true))
        );

        assertEquals(2, catalog.getTableCount());
        assertTrue(catalog.tableExists("students"));
        assertFalse(catalog.tableExists("teachers"));
        assertEquals(2, catalog.getTableNames().size());
    }
}