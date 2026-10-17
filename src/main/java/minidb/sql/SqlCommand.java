
package minidb.sql;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface SqlCommand
        permits SqlCommand.CreateDatabase,
                SqlCommand.UseDatabase,
                SqlCommand.CreateTable,
                SqlCommand.Insert,
                SqlCommand.Select,
                SqlCommand.Update,
                SqlCommand.Delete {

    // CREATE DATABASE college
    record CreateDatabase(String databaseName)
            implements SqlCommand {
        public CreateDatabase {
            requireName(databaseName, "databaseName");
        }
    }

    // USE college
    record UseDatabase(String databaseName)
            implements SqlCommand {
        public UseDatabase {
            requireName(databaseName, "databaseName");
        }
    }

    // CREATE TABLE students (...)
    record CreateTable(
            String tableName,
            List<ColumnDefinition> columns
    ) implements SqlCommand {
        public CreateTable {
            requireName(tableName, "tableName");
            columns = List.copyOf(
                    Objects.requireNonNull(columns, "columns")
            );

            if (columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "A table must have at least one column"
                );
            }
        }
    }

    enum DataType {
        INT,
        VARCHAR
    }

    record ColumnDefinition(
            String name,
            DataType dataType,
            Integer varcharLength,
            boolean primaryKey
    ) {
        public ColumnDefinition {
            requireName(name, "column name");
            Objects.requireNonNull(dataType, "dataType");

            if (dataType == DataType.VARCHAR) {
                if (varcharLength == null || varcharLength <= 0) {
                    throw new IllegalArgumentException(
                            "VARCHAR length must be positive"
                    );
                }
            } else if (varcharLength != null) {
                throw new IllegalArgumentException(
                        "INT cannot have a VARCHAR length"
                );
            }
        }
    }

    // SQL literal values
    sealed interface SqlValue
            permits IntegerValue, StringValue {
    }

    record IntegerValue(long value) implements SqlValue {
    }

    record StringValue(String value) implements SqlValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    // INSERT INTO students VALUES (...)
    record Insert(
            String tableName,
            List<SqlValue> values
    ) implements SqlCommand {
        public Insert {
            requireName(tableName, "tableName");
            values = List.copyOf(
                    Objects.requireNonNull(values, "values")
            );

            if (values.isEmpty()) {
                throw new IllegalArgumentException(
                        "INSERT must contain at least one value"
                );
            }
        }
    }

    // SELECT * or SELECT name, age FROM students ...
    record Select(
            boolean selectAll,
            List<String> columns,
            String tableName,
            Optional<Condition> condition
    ) implements SqlCommand {
        public Select {
            columns = List.copyOf(
                    Objects.requireNonNull(columns, "columns")
            );
            requireName(tableName, "tableName");
            condition = Objects.requireNonNull(
                    condition, "condition"
            );

            if (selectAll && !columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "SELECT * cannot also specify columns"
                );
            }

            if (!selectAll && columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "SELECT must specify columns or use *"
                );
            }

            columns.forEach(
                    column -> requireName(column, "column name")
            );
        }
    }

    enum ComparisonOperator {
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        LESS_THAN,
        GREATER_EQUAL,
        LESS_EQUAL
    }

    record Condition(
            String columnName,
            ComparisonOperator operator,
            SqlValue value
    ) {
        public Condition {
            requireName(columnName, "columnName");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
        }
    }

    // UPDATE students SET age = 21 WHERE id = 1
    record Update(
            String tableName,
            String columnName,
            SqlValue value,
            Optional<Condition> condition
    ) implements SqlCommand {
        public Update {
            requireName(tableName, "tableName");
            requireName(columnName, "columnName");
            Objects.requireNonNull(value, "value");
            condition = Objects.requireNonNull(
                    condition, "condition"
            );
        }
    }

    // DELETE FROM students WHERE id = 1
    record Delete(
            String tableName,
            Optional<Condition> condition
    ) implements SqlCommand {
        public Delete {
            requireName(tableName, "tableName");
            condition = Objects.requireNonNull(
                    condition, "condition"
            );
        }
    }

    private static void requireName(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank"
            );
        }
    }
}
