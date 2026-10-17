
package minidb.sql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private SqlCommand parse(String sql) {
        return new Parser(new Lexer(sql).scanTokens()).parse();
    }

    @Test
    void parsesCreateDatabase() {
        assertEquals(
                new SqlCommand.CreateDatabase("college"),
                parse("CREATE DATABASE college;"));
    }

    @Test
    void parsesUseDatabaseWithoutSemicolon() {
        assertEquals(
                new SqlCommand.UseDatabase("college"),
                parse("USE college"));
    }

    @Test
    void parsesCreateTable() {
        SqlCommand.CreateTable command = assertInstanceOf(
                SqlCommand.CreateTable.class,
                parse("CREATE TABLE students "
                        + "(id INT PRIMARY KEY, "
                        + "name VARCHAR(50), age INT);"));

        assertEquals("students", command.tableName());
        assertEquals(3, command.columns().size());
        assertTrue(command.columns().get(0).primaryKey());
        assertEquals(
                SqlCommand.DataType.VARCHAR,
                command.columns().get(1).dataType());
        assertEquals(50, command.columns().get(1).varcharLength());
    }

    @Test
    void parsesInsertWithIntegerAndStringValues() {
        SqlCommand.Insert command = assertInstanceOf(
                SqlCommand.Insert.class,
                parse("INSERT INTO students "
                        + "VALUES (1, 'Alice', 20);"));

        assertEquals("students", command.tableName());
        assertEquals(
                List.of(
                        new SqlCommand.IntegerValue(1),
                        new SqlCommand.StringValue("Alice"),
                        new SqlCommand.IntegerValue(20)),
                command.values());
    }

    @Test
    void parsesSelectAll() {
        assertEquals(
                new SqlCommand.Select(
                        true,
                        List.of(),
                        "students",
                        Optional.empty()),
                parse("SELECT * FROM students;"));
    }

    @Test
    void parsesSelectWithWhereCondition() {
        SqlCommand.Select command = assertInstanceOf(
                SqlCommand.Select.class,
                parse("SELECT name, age FROM students "
                        + "WHERE age >= 18"));

        assertEquals(List.of("name", "age"), command.columns());

        assertEquals(
                new SqlCommand.Condition(
                        "age",
                        SqlCommand.ComparisonOperator.GREATER_EQUAL,
                        new SqlCommand.IntegerValue(18)),
                command.condition().orElseThrow());
    }

    @Test
    void parsesUpdate() {
        assertEquals(
                new SqlCommand.Update(
                        "students",
                        "age",
                        new SqlCommand.IntegerValue(21),
                        Optional.of(
                                new SqlCommand.Condition(
                                        "id",
                                        SqlCommand.ComparisonOperator.EQUAL,
                                        new SqlCommand.IntegerValue(1)))),
                parse("UPDATE students SET age = 21 WHERE id = 1;"));
    }

    @Test
    void parsesDelete() {
        assertEquals(
                new SqlCommand.Delete(
                        "students",
                        Optional.of(
                                new SqlCommand.Condition(
                                        "id",
                                        SqlCommand.ComparisonOperator.NOT_EQUAL,
                                        new SqlCommand.IntegerValue(1)))),
                parse("DELETE FROM students WHERE id != 1;"));
    }

    @Test
    void decodesDoubledQuotesInStringValue() {
        assertEquals(
                new SqlCommand.Insert(
                        "authors",
                        List.of(
                                new SqlCommand.StringValue("O'Brien"))),
                parse("INSERT INTO authors VALUES ('O''Brien');"));
    }

    @Test
    void rejectsMissingSelectColumn() {
        ParserException exception = assertThrows(
                ParserException.class,
                () -> parse("SELECT FROM students;"));

        assertTrue(exception.getMessage().contains("column name"));
        assertTrue(exception.getMessage().contains("line"));
    }

    @Test
    void rejectsMissingWhereValue() {
        assertThrows(
                ParserException.class,
                () -> parse("SELECT * FROM students WHERE age >;"));
    }

    @Test
    void rejectsMissingCreateTableParenthesis() {
        assertThrows(
                ParserException.class,
                () -> parse("CREATE TABLE students id INT"));
    }

    @Test
    void rejectsUnexpectedTrailingTokens() {
        assertThrows(
                ParserException.class,
                () -> parse("USE college extra"));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(
                ParserException.class,
                () -> parse(""));
    }

    @Test
    void rejectsIntegerOutsideLongRange() {
        assertThrows(
                ParserException.class,
                () -> parse(
                        "INSERT INTO numbers VALUES "
                                + "(999999999999999999999999);"));
    }

    @Test
    void rejectsInvalidVarcharLength() {
    assertThrows(
            ParserException.class,
            () -> parse(
                    "CREATE TABLE students "
                            + "(name VARCHAR(0));"
            )
    );
    }
}
