package minidb.execution;

import minidb.buffer.BufferPool;

import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.ComparisonOperator;
import minidb.sql.SqlCommand.Condition;
import minidb.sql.SqlCommand.DataType;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.Select;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;
import minidb.storage.DiskStorage;
import minidb.storage.TableStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SelectExecutorTest {

    @TempDir
    Path tempDir;

    private static List<ColumnDefinition> studentSchema() {
        return List.of(
                new ColumnDefinition(
                        "id", DataType.INT, null, true
                ),
                new ColumnDefinition(
                        "name", DataType.VARCHAR, 50, false
                ),
                new ColumnDefinition(
                        "age", DataType.INT, null, false
                )
        );
    }

    private TableStorage createStudents(DiskStorage disk)
            throws Exception {
        BufferPool pool = new BufferPool(3, disk, null);
        TableStorage tables = new TableStorage(disk, pool);

        tables.createTable("students", studentSchema());

        tables.insert("students", List.of(
                new IntegerValue(1),
                new StringValue("Alice"),
                new IntegerValue(20)
        ));

        tables.insert("students", List.of(
                new IntegerValue(2),
                new StringValue("Bob"),
                new IntegerValue(16)
        ));

        tables.insert("students", List.of(
                new IntegerValue(3),
                new StringValue("Cara"),
                new IntegerValue(22)
        ));

        return tables;
    }

    @Test
    void selectAllReturnsEveryRowAndColumn() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-all.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    true,
                    List.of(),
                    "students",
                    Optional.empty()
            );

            List<List<SqlValue>> result = executor.execute(command);

            assertEquals(3, result.size());
            assertEquals(
                    List.of(
                            new IntegerValue(1),
                            new StringValue("Alice"),
                            new IntegerValue(20)
                    ),
                    result.get(0)
            );
        }
    }

    @Test
    void selectsSpecificColumns() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-columns.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    false,
                    List.of("name", "id"),
                    "students",
                    Optional.empty()
            );

            assertEquals(
                    List.of(
                            List.of(
                                    new StringValue("Alice"),
                                    new IntegerValue(1)
                            ),
                            List.of(
                                    new StringValue("Bob"),
                                    new IntegerValue(2)
                            ),
                            List.of(
                                    new StringValue("Cara"),
                                    new IntegerValue(3)
                            )
                    ),
                    executor.execute(command)
            );
        }
    }

    @Test
    void filtersRowsUsingGreaterThan() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-where.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    false,
                    List.of("name"),
                    "students",
                    Optional.of(new Condition(
                            "age",
                            ComparisonOperator.GREATER_THAN,
                            new IntegerValue(18)
                    ))
            );

            assertEquals(
                    List.of(
                            List.of(new StringValue("Alice")),
                            List.of(new StringValue("Cara"))
                    ),
                    executor.execute(command)
            );
        }
    }

    @Test
    void supportsNotEqualComparison() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-not-equal.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    true,
                    List.of(),
                    "students",
                    Optional.of(new Condition(
                            "age",
                            ComparisonOperator.NOT_EQUAL,
                            new IntegerValue(20)
                    ))
            );

            assertEquals(2, executor.execute(command).size());
        }
    }

    @Test
    void rejectsUnknownSelectedColumn() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-invalid-column.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    false,
                    List.of("email"),
                    "students",
                    Optional.empty()
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> executor.execute(command)
            );
        }
    }

    @Test
    void rejectsMismatchedWhereValueType() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("select-invalid-type.db"))) {

            TableStorage tables = createStudents(disk);
            SelectExecutor executor = new SelectExecutor(tables);

            Select command = new Select(
                    true,
                    List.of(),
                    "students",
                    Optional.of(new Condition(
                            "age",
                            ComparisonOperator.EQUAL,
                            new StringValue("20")
                    ))
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> executor.execute(command)
            );
        }
    }
}