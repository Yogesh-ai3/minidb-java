package minidb.catalog;

import minidb.sql.SqlCommand.ColumnDefinition;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TableSchema {
    private final String tableName;
    private final List<ColumnDefinition> columns;

    public TableSchema(String tableName, List<ColumnDefinition> columns) {
        Objects.requireNonNull(tableName, "tableName");

        if (tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be empty");
        }

        this.tableName = tableName.trim().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(columns, "columns");

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "A table must have at least one column");
        }

        Set<String> columnNames = new HashSet<>();

        for (ColumnDefinition column : columns) {
            Objects.requireNonNull(column, "Column definition");

            if (column.name() == null || column.name().isBlank()) {
                throw new IllegalArgumentException(
                        "Column name cannot be empty");
            }

            if (!columnNames.add(
                column.name().trim().toLowerCase(Locale.ROOT)))  {
                throw new IllegalArgumentException(
                        "Duplicate column: " + column.name());
            }
        }

        this.columns = List.copyOf(columns);
    }

    public String getTableName() {
        return tableName;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public int getColumnIndex(String columnName) {
        Objects.requireNonNull(columnName, "columnName");

        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(columnName.trim())) {
                return i;
            }
        }

        throw new IllegalArgumentException(
                "Unknown column '" + columnName
                        + "' in table '" + tableName + "'");
    }

    public ColumnDefinition getColumn(String columnName) {
        return columns.get(getColumnIndex(columnName));
    }
}