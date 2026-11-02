package minidb.execution;

import minidb.sql.SqlCommand;
import minidb.storage.TableStorage;

import java.io.IOException;
import java.util.Objects;

public final class InsertExecutor {

    private final TableStorage tableStorage;

    public InsertExecutor(TableStorage tableStorage) {
        this.tableStorage = Objects.requireNonNull(
                tableStorage, "tableStorage"
        );
    }

    public void execute(SqlCommand.Insert command) throws IOException {
        Objects.requireNonNull(command, "command");

        tableStorage.insert(
                command.tableName(),
                command.values()
        );
    }
}