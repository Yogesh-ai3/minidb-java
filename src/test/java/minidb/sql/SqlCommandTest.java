
package minidb.sql;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqlCommandTest {

    @Test
    void createsDatabaseCommand() {
        SqlCommand command =
                new SqlCommand.CreateDatabase("college");

        assertEquals(
                "college",
                ((SqlCommand.CreateDatabase) command).databaseName()
        );
    }

    @Test
    void createsTableWithColumnDefinitions() {
        SqlCommand.CreateTable command =
                new SqlCommand.CreateTable(
                        "students",
                        List.of(
                                new SqlCommand.ColumnDefinition(
                                        "id",
                                        SqlCommand.DataType.INT,
                                        null,
                                        true
                                ),
                                new SqlCommand.ColumnDefinition(
                                        "name",
                                        SqlCommand.DataType.VARCHAR,
                                        50,
                                        false
                                )
                        )
                );

        assertEquals("students", command.tableName());
        assertEquals(2, command.columns().size());
        assertTrue(command.columns().get(0).primaryKey());
        assertEquals(
                50,
                command.columns().get(1).varcharLength()
        );
    }

    @Test
    void distinguishesIntegerAndStringValues() {
        SqlCommand.SqlValue number =
                new SqlCommand.IntegerValue(18);

        SqlCommand.SqlValue text =
                new SqlCommand.StringValue("18");

        assertNotEquals(number, text);
        assertInstanceOf(SqlCommand.IntegerValue.class, number);
        assertInstanceOf(SqlCommand.StringValue.class, text);
    }

    @Test
    void representsSelectAllWithoutExplicitColumns() {
        SqlCommand.Select command =
                new SqlCommand.Select(
                        true,
                        List.of(),
                        "students",
                        Optional.empty()
                );

        assertTrue(command.selectAll());
        assertTrue(command.columns().isEmpty());
        assertTrue(command.condition().isEmpty());
    }

    @Test
    void representsWhereCondition() {
        SqlCommand.Condition condition =
                new SqlCommand.Condition(
                        "age",
                        SqlCommand.ComparisonOperator.GREATER_THAN,
                        new SqlCommand.IntegerValue(18)
                );

        SqlCommand.Select command =
                new SqlCommand.Select(
                        false,
                        List.of("name"),
                        "students",
                        Optional.of(condition)
                );

        assertTrue(command.condition().isPresent());
        assertEquals(
                "age",
                command.condition().orElseThrow().columnName()
        );
    }

    @Test
    void rejectsEmptyTableColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlCommand.CreateTable(
                        "students",
                        List.of()
                )
        );
    }

    @Test
    void rejectsInvalidVarcharLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlCommand.ColumnDefinition(
                        "name",
                        SqlCommand.DataType.VARCHAR,
                        0,
                        false
                )
        );
    }

    @Test
    void commandListsCannotBeModifiedExternally() {
        List<SqlCommand.SqlValue> values = new ArrayList<>();
        values.add(new SqlCommand.IntegerValue(1));

        SqlCommand.Insert command =
                new SqlCommand.Insert("students", values);

        values.add(new SqlCommand.IntegerValue(2));

        assertEquals(1, command.values().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> command.values().add(
                        new SqlCommand.IntegerValue(3)
                )
        );
    }

    @Test
    void rejectsSelectWithNeitherColumnsNorStar() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlCommand.Select(
                        false,
                        List.of(),
                        "students",
                        Optional.empty()
                )
        );
    }
}
