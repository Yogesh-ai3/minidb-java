package minidb.execution;


import minidb.sql.SqlCommand.ComparisonOperator;
import minidb.sql.SqlCommand.Condition;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.Select;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;
import minidb.storage.TableStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SelectExecutor {

    private final TableStorage tableStorage;

    public SelectExecutor(TableStorage tableStorage) {
        this.tableStorage = Objects.requireNonNull(
                tableStorage, "tableStorage"
        );
    }

    public List<List<SqlValue>> execute(Select command)
            throws IOException {
        Objects.requireNonNull(command, "command");

        List<List<SqlValue>> rows =
                tableStorage.readAll(command.tableName());

        Optional<Condition> condition = command.condition();
        int conditionIndex = -1;

        if (condition.isPresent()) {
            conditionIndex = tableStorage.getColumnIndex(
                    command.tableName(),
                    condition.get().columnName()
            );
        }

        List<Integer> selectedIndexes = new ArrayList<>();

        if (!command.selectAll()) {
            for (String columnName : command.columns()) {
                selectedIndexes.add(
                        tableStorage.getColumnIndex(
                                command.tableName(), columnName
                        )
                );
            }
        }

        List<List<SqlValue>> results = new ArrayList<>();

        for (List<SqlValue> row : rows) {
            if (condition.isPresent()
                    && !matches(
                            row.get(conditionIndex),
                            condition.get()
                    )) {
                continue;
            }

            if (command.selectAll()) {
                results.add(row);
            } else {
                List<SqlValue> projected = new ArrayList<>();

                for (int index : selectedIndexes) {
                    projected.add(row.get(index));
                }

                results.add(List.copyOf(projected));
            }
        }

        return List.copyOf(results);
    }

    private boolean matches(SqlValue actual, Condition condition) {
        SqlValue expected = condition.value();
        int comparison = compareValues(actual, expected);

        ComparisonOperator operator = condition.operator();

        return switch (operator) {
            case EQUAL -> comparison == 0;
            case NOT_EQUAL -> comparison != 0;
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
            case GREATER_EQUAL -> comparison >= 0;
            case LESS_EQUAL -> comparison <= 0;
        };
    }

    private int compareValues(SqlValue actual, SqlValue expected) {
        if (actual instanceof IntegerValue left
                && expected instanceof IntegerValue right) {
            return Long.compare(left.value(), right.value());
        }

        if (actual instanceof StringValue left
                && expected instanceof StringValue right) {
            return left.value().compareTo(right.value());
        }

        throw new IllegalArgumentException(
                "WHERE comparison requires values of matching types"
        );
    }
}