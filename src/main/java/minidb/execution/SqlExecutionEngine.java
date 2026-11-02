package minidb.execution;

import minidb.sql.SqlCommand;
import minidb.sql.SqlCommand.CreateTable;
import minidb.sql.SqlCommand.Insert;
import minidb.sql.SqlCommand.Select;
import minidb.sql.SqlCommand.SqlValue;
import minidb.storage.TableStorage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import minidb.sql.SqlCommand.Update;
import minidb.sql.SqlCommand.Delete;

public final class SqlExecutionEngine {

    private final TableStorage tableStorage;
    private final InsertExecutor insertExecutor;
    private final SelectExecutor selectExecutor;

    public SqlExecutionEngine(TableStorage tableStorage) {
        this.tableStorage = Objects.requireNonNull(
                tableStorage, "tableStorage"
        );

        this.insertExecutor = new InsertExecutor(tableStorage);
        this.selectExecutor = new SelectExecutor(tableStorage);
    }

    public String execute(SqlCommand command) throws IOException {
        Objects.requireNonNull(command, "command");

        if (command instanceof CreateTable createTable) {
            tableStorage.createTable(
                    createTable.tableName(),
                    createTable.columns()
            );

            return "Table '" + createTable.tableName()
                    + "' created successfully.";
        }

        if (command instanceof Insert insert) {
            insertExecutor.execute(insert);
            return "1 row inserted into '"
                    + insert.tableName() + "'.";
        }

        if (command instanceof Select select) {
            List<List<SqlValue>> rows = selectExecutor.execute(select);

            List<String> columns = select.selectAll()
                    ? tableStorage.getColumns(select.tableName())
                            .stream()
                            .map(column -> column.name())
                            .toList()
                    : select.columns();

            return formatResults(columns, rows);
        }

        if (command instanceof Update update) {
            int affectedRows = tableStorage.update(
                    update.tableName(),
                    update.columnName(),
                    update.value(),
                    update.condition()
            );
        
            return affectedRows + " row(s) updated in '"
                    + update.tableName() + "'.";
        }
        
        if (command instanceof Delete delete) {
            int affectedRows = tableStorage.delete(
                    delete.tableName(),
                    delete.condition()
            );
        
            return affectedRows + " row(s) deleted from '"
                    + delete.tableName() + "'.";
        }

        throw new UnsupportedOperationException(
                "Execution is not implemented for "
                        + command.getClass().getSimpleName()
        );
    }

    private String formatResults(
            List<String> columns,
            List<List<SqlValue>> rows
    ) {
        StringBuilder output = new StringBuilder();

        output.append(String.join(" | ", columns)).append('\n');

        output.append(columns.stream()
                .map(column -> "-".repeat(Math.max(3, column.length())))
                .collect(Collectors.joining("-+-")));

        output.append('\n');

        for (List<SqlValue> row : rows) {
            output.append(row.stream()
                    .map(this::formatValue)
                    .collect(Collectors.joining(" | ")));

            output.append('\n');
        }

        output.append('(').append(rows.size()).append(" rows)");

        return output.toString();
    }

    private String formatValue(SqlValue value) {
        if (value instanceof SqlCommand.IntegerValue integerValue) {
            return Long.toString(integerValue.value());
        }

        if (value instanceof SqlCommand.StringValue stringValue) {
            return stringValue.value();
        }

        return String.valueOf(value);
    }
}