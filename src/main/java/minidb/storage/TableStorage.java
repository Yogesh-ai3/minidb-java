package minidb.storage;

import minidb.catalog.Catalog;
import minidb.buffer.BufferFrame;
import minidb.buffer.BufferPool;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import minidb.sql.SqlCommand.Condition;
import minidb.sql.SqlCommand.ComparisonOperator;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import minidb.database.DatabaseMetadataStore;

public final class TableStorage {

    private static final class TableInfo {
        private final List<Long> pageIds;
        private final List<ColumnDefinition> columns;

        private TableInfo(
                List<Long> pageIds,
                List<ColumnDefinition> columns) {
            this.pageIds = pageIds;
            this.columns = List.copyOf(columns);
        }
    }

    private final DiskStorage diskStorage;
    private final BufferPool bufferPool;
    private final Map<String, TableInfo> tables = new HashMap<>();
    private final Catalog catalog;
    private final Path metadataPath;

    public TableStorage(DiskStorage diskStorage, BufferPool bufferPool) {
        this(diskStorage, bufferPool, new Catalog(), null);
    }

    public TableStorage(
            DiskStorage diskStorage,
            BufferPool bufferPool,
            Catalog catalog,
            Path metadataPath) {

        this.diskStorage = Objects.requireNonNull(diskStorage, "diskStorage");
        this.bufferPool = Objects.requireNonNull(bufferPool, "bufferPool");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.metadataPath = metadataPath;
    }

    /**
     * Creates a table without column metadata.
     * Retained for existing storage-level tests.
     */
    public synchronized void createTable(String tableName)
            throws IOException {
        createTable(tableName, List.of());
    }

    public synchronized void createTable(
            String tableName,
            List<ColumnDefinition> columns) throws IOException {

        String key = normalizeName(tableName);
        Objects.requireNonNull(columns, "columns");

        if (tables.containsKey(key)) {
            throw new IllegalArgumentException("Table already exists: " + tableName);
        }

        List<ColumnDefinition> schema = List.copyOf(columns);
        if (!schema.isEmpty()) {
            validateSchema(schema);
        }

        boolean catalogRegistered = false;

        try {
            if (!schema.isEmpty()) {
                catalog.createTable(key, schema);
                catalogRegistered = true;
            }

            long pageId = diskStorage.allocatePage().getPageId();
            BufferFrame frame = bufferPool.getPage(pageId);
            frame.writeBytes(0, RecordPage.initialize());
            bufferPool.flushPage(pageId);

            tables.put(key, new TableInfo(
                    new ArrayList<>(List.of(pageId)),
                    schema));

            saveMetadata();
        } catch (IOException | RuntimeException exception) {
            tables.remove(key);

            if (catalogRegistered) {
                catalog.dropTable(key);
            }

            throw exception;
        }
    }

    public synchronized void insert(
            String tableName,
            List<SqlValue> values) throws IOException {
        Objects.requireNonNull(values, "values");

        TableInfo table = getTable(tableName);
        validateRow(tableName, table, values);
        validatePrimaryKey(tableName, table, values);

        byte[] encodedRecord = RecordCodec.encode(values);

        if ((long) encodedRecord.length + Integer.BYTES > Page.PAGE_SIZE - 12) {
            throw new IllegalArgumentException(
                    "Record is too large to fit in an empty page");
        }

        int lastIndex = table.pageIds.size() - 1;
        long lastPageId = table.pageIds.get(lastIndex);

        BufferFrame frame = bufferPool.getPage(lastPageId);
        byte[] pageData = frame.getPage().getData();

        if (RecordPage.append(pageData, values)) {
            frame.writeBytes(0, pageData);
            return;
        }

        long newPageId = diskStorage.allocatePage().getPageId();
        BufferFrame newFrame = bufferPool.getPage(newPageId);
        byte[] newPageData = RecordPage.initialize();

        if (!RecordPage.append(newPageData, values)) {
            throw new IllegalArgumentException(
                    "Record is too large to fit in an empty page");
        }

        newFrame.writeBytes(0, newPageData);
        bufferPool.flushPage(newPageId);
        table.pageIds.add(newPageId);

        try {
            saveMetadata();
        } catch (IOException exception) {
            table.pageIds.remove(table.pageIds.size() - 1);
            throw exception;
        }
    }

    public synchronized List<List<SqlValue>> readAll(
            String tableName) throws IOException {
        TableInfo table = getTable(tableName);
        List<List<SqlValue>> result = new ArrayList<>();

        for (long pageId : table.pageIds) {
            BufferFrame frame = bufferPool.getPage(pageId);
            result.addAll(
                    RecordPage.readRecords(frame.getPage().getData()));
        }

        return List.copyOf(result);
    }

    public synchronized List<Long> getTablePageIds(String tableName) {
        return List.copyOf(getTable(tableName).pageIds);
    }

    public synchronized List<ColumnDefinition> getColumns(
            String tableName) {
        String key = normalizeName(tableName);

        if (catalog.tableExists(key)) {
            return catalog.getTable(key).getColumns();
        }

        return getTable(tableName).columns;
    }

    public synchronized int getColumnIndex(
            String tableName,
            String columnName) {
        List<ColumnDefinition> columns = getColumns(tableName);
        String target = normalizeName(columnName);

        for (int i = 0; i < columns.size(); i++) {
            if (normalizeName(columns.get(i).name()).equals(target)) {
                return i;
            }
        }

        throw new IllegalArgumentException(
                "Unknown column '" + columnName
                        + "' in table '" + tableName + "'");
    }

    private void validateSchema(List<ColumnDefinition> columns) {
        Map<String, Boolean> names = new HashMap<>();

        for (ColumnDefinition column : columns) {
            Objects.requireNonNull(column, "column definition");

            String key = normalizeName(column.name());

            if (names.put(key, Boolean.TRUE) != null) {
                throw new IllegalArgumentException(
                        "Duplicate column name: " + column.name());
            }
        }
    }

    private void validateRow(
            String tableName,
            TableInfo table,
            List<SqlValue> values) {
        Objects.requireNonNull(values, "values");

        if (table.columns.isEmpty()) {
            return;
        }

        String key = normalizeName(tableName);

        List<ColumnDefinition> columns = catalog.tableExists(key)
                ? catalog.getTable(key).getColumns()
                : table.columns;

        if (values.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "Expected " + columns.size()
                            + " values but received " + values.size());
        }

        for (int i = 0; i < values.size(); i++) {
            SqlValue value = Objects.requireNonNull(
                    values.get(i), "record value");
            ColumnDefinition column = columns.get(i);

            if (column.dataType() == DataType.INT
                    && !(value instanceof IntegerValue)) {
                throw new IllegalArgumentException(
                        "Column '" + column.name()
                                + "' requires an integer");
            }

            if (column.dataType() == DataType.VARCHAR
                    && !(value instanceof StringValue)) {
                throw new IllegalArgumentException(
                        "Column '" + column.name()
                                + "' requires a string");
            }

            if (value instanceof StringValue stringValue
                    && column.dataType() == DataType.VARCHAR
                    && stringValue.value().length() > column.varcharLength()) {
                throw new IllegalArgumentException(
                        "Value exceeds VARCHAR("
                                + column.varcharLength()
                                + ") for column '" + column.name() + "'");
            }
        }
    }

    private TableInfo getTable(String tableName) {
        TableInfo table = tables.get(normalizeName(tableName));

        if (table == null) {
            throw new IllegalArgumentException(
                    "Table does not exist: " + tableName);
        }

        return table;
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "name");

        String key = name.trim().toLowerCase(Locale.ROOT);

        if (key.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        return key;
    }

    private void validatePrimaryKey(
            String tableName,
            TableInfo table,
            List<SqlValue> values) throws IOException {
        String key = normalizeName(tableName);

        List<ColumnDefinition> columns = catalog.tableExists(key)
                ? catalog.getTable(key).getColumns()
                : table.columns;

        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            ColumnDefinition column = columns.get(columnIndex);

            if (!column.primaryKey()) {
                continue;
            }

            SqlValue candidateKey = values.get(columnIndex);

            for (List<SqlValue> existingRow : readAll(tableName)) {
                if (candidateKey.equals(existingRow.get(columnIndex))) {
                    throw new IllegalArgumentException(
                            "Duplicate primary key value '"
                                    + candidateKey
                                    + "' for column '"
                                    + column.name()
                                    + "'");
                }
            }
        }
    }

    private void saveMetadata() throws IOException {
        if (metadataPath == null) {
            return;
        }

        Map<String, List<Long>> pageMappings = new HashMap<>();

        for (String tableName : catalog.getTableNames()) {
            TableInfo table = tables.get(normalizeName(tableName));

            if (table == null) {
                throw new IOException(
                        "Missing storage mapping for table: " + tableName);
            }

            pageMappings.put(tableName, List.copyOf(table.pageIds));
        }

        DatabaseMetadataStore.save(metadataPath, catalog, pageMappings);
    }

    public synchronized void loadMetadata() throws IOException {
        if (metadataPath == null) {
            throw new IllegalStateException(
                    "No metadata path was configured for this database");
        }

        if (!tables.isEmpty() || catalog.getTableCount() != 0) {
            throw new IllegalStateException(
                    "Metadata can only be loaded into an empty catalog and storage");
        }

        List<DatabaseMetadataStore.TableMetadata> loaded = DatabaseMetadataStore.load(metadataPath);

        long pageCount = diskStorage.getPageCount();

        // Validate every page reference before changing in-memory state.
        for (DatabaseMetadataStore.TableMetadata metadata : loaded) {
            for (long pageId : metadata.pageIds()) {
                if (pageId >= pageCount) {
                    throw new IOException(
                            "Metadata for table '" + metadata.tableName()
                                    + "' references missing page " + pageId);
                }
            }
        }

        try {
            for (DatabaseMetadataStore.TableMetadata metadata : loaded) {
                String key = normalizeName(metadata.tableName());

                catalog.createTable(key, metadata.columns());

                tables.put(key, new TableInfo(
                        new ArrayList<>(metadata.pageIds()),
                        metadata.columns()));
            }
        } catch (RuntimeException exception) {
            // Avoid leaving a partially restored catalog if restoration fails.
            for (DatabaseMetadataStore.TableMetadata metadata : loaded) {
                String key = normalizeName(metadata.tableName());
                tables.remove(key);

                if (catalog.tableExists(key)) {
                    catalog.dropTable(key);
                }
            }

            throw new IOException("Could not restore database metadata", exception);
        }
    }

    public synchronized int update(
            String tableName,
            String columnName,
            SqlValue value,
            Optional<Condition> condition) throws IOException {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(condition, "condition");

        TableInfo table = getTable(tableName);
        int targetIndex = getColumnIndex(tableName, columnName);
        ColumnDefinition targetColumn = getColumns(tableName).get(targetIndex);

        // Validate the assignment even if no rows match WHERE.
        validateColumnValue(targetColumn, value);
        condition.ifPresent(c -> validateCondition(tableName, c));

        List<List<SqlValue>> updatedRows = new ArrayList<>();
        int affectedRows = 0;

        for (List<SqlValue> row : readAll(tableName)) {
            List<SqlValue> updatedRow = new ArrayList<>(row);

            if (condition.isEmpty()
                    || matchesCondition(row, tableName, condition.get())) {
                updatedRow.set(targetIndex, value);
                validateRow(tableName, table, updatedRow);
                affectedRows++;
            }

            updatedRows.add(List.copyOf(updatedRow));
        }

        // Check the complete resulting table, not each row against old data.
        validatePrimaryKeyUniqueness(tableName, updatedRows);

        if (affectedRows > 0) {
            rewriteRows(table, updatedRows);
        }

        return affectedRows;
    }

    public synchronized int delete(
            String tableName,
            Optional<Condition> condition) throws IOException {
        Objects.requireNonNull(condition, "condition");

        TableInfo table = getTable(tableName);
        condition.ifPresent(c -> validateCondition(tableName, c));

        List<List<SqlValue>> remainingRows = new ArrayList<>();
        int affectedRows = 0;

        for (List<SqlValue> row : readAll(tableName)) {
            boolean matches = condition.isEmpty()
                    || matchesCondition(row, tableName, condition.get());

            if (matches) {
                affectedRows++;
            } else {
                remainingRows.add(row);
            }
        }

        if (affectedRows > 0) {
            rewriteRows(table, remainingRows);
        }

        return affectedRows;
    }

    private void validateColumnValue(
            ColumnDefinition column,
            SqlValue value) {
        if (column.dataType() == DataType.INT
                && !(value instanceof IntegerValue)) {
            throw new IllegalArgumentException(
                    "Column '" + column.name() + "' requires an integer");
        }

        if (column.dataType() == DataType.VARCHAR
                && !(value instanceof StringValue)) {
            throw new IllegalArgumentException(
                    "Column '" + column.name() + "' requires a string");
        }

        if (column.dataType() == DataType.VARCHAR
                && value instanceof StringValue stringValue
                && stringValue.value().length() > column.varcharLength()) {
            throw new IllegalArgumentException(
                    "Value exceeds VARCHAR(" + column.varcharLength()
                            + ") for column '" + column.name() + "'");
        }
    }

    private void validateCondition(
            String tableName,
            Condition condition) {
        int index = getColumnIndex(tableName, condition.columnName());
        ColumnDefinition column = getColumns(tableName).get(index);
        validateColumnValue(column, condition.value());
    }

    private boolean matchesCondition(
            List<SqlValue> row,
            String tableName,
            Condition condition) {
        int index = getColumnIndex(tableName, condition.columnName());
        int comparison = compareValues(row.get(index), condition.value());

        return switch (condition.operator()) {
            case EQUAL -> comparison == 0;
            case NOT_EQUAL -> comparison != 0;
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
            case GREATER_EQUAL -> comparison >= 0;
            case LESS_EQUAL -> comparison <= 0;
        };
    }

    private int compareValues(SqlValue left, SqlValue right) {
        if (left instanceof IntegerValue leftInteger
                && right instanceof IntegerValue rightInteger) {
            return Long.compare(leftInteger.value(), rightInteger.value());
        }

        if (left instanceof StringValue leftString
                && right instanceof StringValue rightString) {
            return leftString.value().compareTo(rightString.value());
        }

        throw new IllegalArgumentException(
                "Cannot compare values of different data types");
    }

    private void validatePrimaryKeyUniqueness(
            String tableName,
            List<List<SqlValue>> rows) {
        List<ColumnDefinition> columns = getColumns(tableName);

        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            ColumnDefinition column = columns.get(columnIndex);

            if (!column.primaryKey()) {
                continue;
            }

            Set<SqlValue> seenKeys = new HashSet<>();

            for (List<SqlValue> row : rows) {
                if (!seenKeys.add(row.get(columnIndex))) {
                    throw new IllegalArgumentException(
                            "Duplicate primary key value '"
                                    + row.get(columnIndex)
                                    + "' for column '" + column.name() + "'");
                }
            }
        }
    }

    /**
     * Rebuilds the table's records into its existing pages.
     * Additional pages are allocated if updated records need more space.
     */
    private void rewriteRows(
            TableInfo table,
            List<List<SqlValue>> rows) throws IOException {
        List<byte[]> packedPages = new ArrayList<>();
        byte[] currentPage = RecordPage.initialize();

        for (List<SqlValue> row : rows) {
            if (!RecordPage.append(currentPage, row)) {
                if (RecordPage.readRecords(currentPage).isEmpty()) {
                    throw new IllegalArgumentException(
                            "Record is too large to fit in an empty page");
                }

                packedPages.add(currentPage);
                currentPage = RecordPage.initialize();

                if (!RecordPage.append(currentPage, row)) {
                    throw new IllegalArgumentException(
                            "Record is too large to fit in an empty page");
                }
            }
        }

        // A table must retain at least one initialized page.
        packedPages.add(currentPage);

        boolean addedPage = false;

        while (table.pageIds.size() < packedPages.size()) {
            long newPageId = diskStorage.allocatePage().getPageId();
            table.pageIds.add(newPageId);
            addedPage = true;
        }

        // Clear unused old pages so deleted records cannot reappear.
        while (packedPages.size() < table.pageIds.size()) {
            packedPages.add(RecordPage.initialize());
        }

        for (int i = 0; i < table.pageIds.size(); i++) {
            long pageId = table.pageIds.get(i);
            BufferFrame frame = bufferPool.getPage(pageId);
            frame.writeBytes(0, packedPages.get(i));
        }

        if (addedPage) {
            saveMetadata();
        }
    }

}