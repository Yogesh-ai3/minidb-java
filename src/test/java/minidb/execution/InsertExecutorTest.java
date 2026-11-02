package minidb.execution;

import minidb.buffer.BufferPool;
import minidb.sql.SqlCommand;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.StringValue;
import minidb.storage.DiskStorage;
import minidb.storage.TableStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InsertExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void executesParsedInsertCommand() throws Exception {
        try (DiskStorage disk = new DiskStorage(
                tempDir.resolve("minidb.db"))) {

            BufferPool pool = new BufferPool(2, disk, null);
            TableStorage tables = new TableStorage(disk, pool);
            tables.createTable("students");

            InsertExecutor executor = new InsertExecutor(tables);

            SqlCommand.Insert command = new SqlCommand.Insert(
                    "students",
                    List.of(
                            new IntegerValue(10),
                            new StringValue("Maya")
                    )
            );

            executor.execute(command);

            assertEquals(
                    List.of(List.of(
                            new IntegerValue(10),
                            new StringValue("Maya")
                    )),
                    tables.readAll("students")
            );
        }
    }
}