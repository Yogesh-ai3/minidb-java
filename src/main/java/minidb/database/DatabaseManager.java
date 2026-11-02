
package minidb.database;

import minidb.buffer.BufferPool;
import minidb.catalog.Catalog;
import minidb.execution.SqlExecutionEngine;
import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;
import minidb.sql.SqlCommand;
import minidb.sql.SqlCommand.CreateDatabase;
import minidb.sql.SqlCommand.UseDatabase;
import minidb.storage.DiskStorage;
import minidb.storage.TableStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DatabaseManager implements AutoCloseable {

    private final Path rootDirectory;
    private final SystemEventBus eventBus;

    private String currentDatabaseName;
    private DiskStorage diskStorage;
    private BufferPool bufferPool;
    private TableStorage tableStorage;
    private SqlExecutionEngine executionEngine;

    public DatabaseManager(Path rootDirectory, SystemEventBus eventBus)
            throws IOException {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath()
                .normalize();
        this.eventBus = eventBus;

        Files.createDirectories(this.rootDirectory);
    }

    public synchronized String execute(SqlCommand command) throws IOException {
        Objects.requireNonNull(command, "command");

        if (command instanceof CreateDatabase createDatabase) {
            createDatabase(createDatabase.databaseName());
            return "Database '" + normalizeName(createDatabase.databaseName())
                    + "' created successfully.";
        }

        if (command instanceof UseDatabase useDatabase) {
            useDatabase(useDatabase.databaseName());
            return "Using database '"
                    + normalizeName(useDatabase.databaseName()) + "'.";
        }

        if (executionEngine == null) {
            throw new IllegalStateException(
                    "No database selected. Run USE database_name first.");
        }

        return executionEngine.execute(command);
    }

    public synchronized String getCurrentDatabaseName() {
        return currentDatabaseName;
    }

    private void createDatabase(String databaseName) throws IOException {
        String name = normalizeName(databaseName);
        Path databaseDirectory = rootDirectory.resolve(name).normalize();

        if (!databaseDirectory.getParent().equals(rootDirectory)) {
            throw new IllegalArgumentException("Invalid database name");
        }

        // createDirectory fails if the database already exists.
        Files.createDirectory(databaseDirectory);

        Path dataPath = databaseDirectory.resolve("minidb.db");
        Path metadataPath = databaseDirectory.resolve("tables.dat");

        try {
            try (DiskStorage ignored = new DiskStorage(dataPath, eventBus)) {
                // Creates the database's empty page-storage file.
            }

            DatabaseMetadataStore.save(
                    metadataPath,
                    new Catalog(),
                    Map.of());

            publish(EventType.PROCESS_COMPLETED,
                    "Created database " + name);
        } catch (IOException | RuntimeException exception) {
            // Remove only files in the newly created directory.
            try {
                Files.deleteIfExists(metadataPath);
                Files.deleteIfExists(metadataPath.resolveSibling("tables.dat.tmp"));
                Files.deleteIfExists(dataPath);
                Files.deleteIfExists(databaseDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void useDatabase(String databaseName) throws IOException {
        String name = normalizeName(databaseName);
        Path databaseDirectory = rootDirectory.resolve(name).normalize();

        if (!databaseDirectory.getParent().equals(rootDirectory)
                || !Files.isDirectory(databaseDirectory)) {
            throw new IOException("Database does not exist: " + name);
        }

        Path dataPath = databaseDirectory.resolve("minidb.db");
        Path metadataPath = databaseDirectory.resolve("tables.dat");

        if (!Files.isRegularFile(dataPath)) {
            throw new IOException("Database storage file is missing: " + dataPath);
        }
        if (!Files.isRegularFile(metadataPath)) {
            throw new IOException("Database metadata file is missing: " + metadataPath);
        }

        if (name.equals(currentDatabaseName)) {
            return;
        }

        // Open and validate the candidate database before switching.
        DiskStorage newDiskStorage = new DiskStorage(dataPath, eventBus);
        BufferPool newBufferPool = null;

        try {
            newBufferPool = new BufferPool(16, newDiskStorage, eventBus);
            Catalog newCatalog = new Catalog();
            TableStorage newTableStorage = new TableStorage(
                    newDiskStorage,
                    newBufferPool,
                    newCatalog,
                    metadataPath);

            newTableStorage.loadMetadata();

            SqlExecutionEngine newExecutionEngine =
                    new SqlExecutionEngine(newTableStorage);

            // Keep the old database selected if opening the new one fails.
            closeCurrentDatabase();

            diskStorage = newDiskStorage;
            bufferPool = newBufferPool;
            tableStorage = newTableStorage;
            executionEngine = newExecutionEngine;
            currentDatabaseName = name;

            publish(EventType.PROCESS_COMPLETED, "Selected database " + name);
        } catch (IOException | RuntimeException exception) {

            try {
                newDiskStorage.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }
    }

    private void closeCurrentDatabase() throws IOException {
        if (diskStorage == null) {
            return;
        }

        IOException failure = null;

        try {
            bufferPool.flushAll();
        } catch (IOException exception) {
            failure = exception;
        }

        try {
            diskStorage.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }

        diskStorage = null;
        bufferPool = null;
        tableStorage = null;
        executionEngine = null;
        currentDatabaseName = null;

        if (failure != null) {
            throw failure;
        }
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "databaseName");
        String normalized = name.trim().toLowerCase(Locale.ROOT);

        if (!normalized.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid database name: " + name);
        }

        return normalized;
    }

    private void publish(EventType type, String message) {
        if (eventBus != null) {
            eventBus.publish(new SystemEvent(type, message));
        }
    }

    @Override
    public synchronized void close() throws IOException {
        closeCurrentDatabase();
    }
}
