
package minidb.database;

import minidb.catalog.Catalog;
import minidb.catalog.TableSchema;
import minidb.sql.SqlCommand.ColumnDefinition;
import minidb.sql.SqlCommand.DataType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DatabaseMetadataStore {

    private static final int MAGIC = 0x4D444D44; // MDMD
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_TABLES = 100_000;
    private static final int MAX_COLUMNS = 10_000;
    private static final int MAX_PAGES_PER_TABLE = 10_000_000;
    private static final int MAX_NAME_LENGTH = 1024;

    private DatabaseMetadataStore() {
    }

    public record TableMetadata(
            String tableName,
            List<ColumnDefinition> columns,
            List<Long> pageIds) {

        public TableMetadata {
            Objects.requireNonNull(tableName, "tableName");
            columns = List.copyOf(columns);
            pageIds = List.copyOf(pageIds);

            if (tableName.isBlank()) {
                throw new IllegalArgumentException("Table name cannot be empty");
            }
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("A table must have at least one column");
            }
            if (pageIds.isEmpty()) {
                throw new IllegalArgumentException("A table must have at least one page");
            }
        }
    }

    public static void save(
            Path metadataPath,
            Catalog catalog,
            Map<String, List<Long>> pageMappings) throws IOException {

        Objects.requireNonNull(metadataPath, "metadataPath");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(pageMappings, "pageMappings");

        List<TableMetadata> tables = new ArrayList<>();
        Set<Long> allPageIds = new HashSet<>();

        for (String tableName : catalog.getTableNames()) {
            TableSchema schema = catalog.getTable(tableName);
            List<Long> pageIds = pageMappings.get(tableName);

            if (pageIds == null || pageIds.isEmpty()) {
                throw new IOException(
                        "Missing page mapping for table: " + tableName);
            }

            for (Long pageId : pageIds) {
                if (pageId == null || pageId < 0) {
                    throw new IOException(
                            "Invalid page ID in table: " + tableName);
                }
                if (!allPageIds.add(pageId)) {
                    throw new IOException(
                            "Page " + pageId + " is assigned to multiple tables");
                }
            }

            tables.add(new TableMetadata(
                    schema.getTableName(),
                    schema.getColumns(),
                    pageIds));
        }

        Path absolutePath = metadataPath.toAbsolutePath();
        Path parent = absolutePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = absolutePath.resolveSibling(
                absolutePath.getFileName() + ".tmp");

        try {
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(temporaryPath)))) {

                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(tables.size());

                for (TableMetadata table : tables) {
                    output.writeUTF(table.tableName());

                    output.writeInt(table.columns().size());
                    for (ColumnDefinition column : table.columns()) {
                        output.writeUTF(column.name());

                        if (column.dataType() == DataType.INT) {
                            output.writeByte(1);
                            output.writeInt(0);
                        } else if (column.dataType() == DataType.VARCHAR) {
                            output.writeByte(2);
                            output.writeInt(column.varcharLength());
                        } else {
                            throw new IOException(
                                    "Unsupported column type: " + column.dataType());
                        }

                        output.writeBoolean(column.primaryKey());
                    }

                    output.writeInt(table.pageIds().size());
                    for (long pageId : table.pageIds()) {
                        output.writeLong(pageId);
                    }
                }
            }

            try {
                Files.move(
                        temporaryPath,
                        absolutePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporaryPath,
                        absolutePath,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    public static List<TableMetadata> load(Path metadataPath)
            throws IOException {

        Objects.requireNonNull(metadataPath, "metadataPath");

        if (!Files.exists(metadataPath)) {
            throw new IOException(
                    "Database metadata file does not exist: " + metadataPath);
        }

        List<TableMetadata> tables = new ArrayList<>();
        Set<String> tableNames = new HashSet<>();
        Set<Long> allPageIds = new HashSet<>();

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(metadataPath)))) {

            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid database metadata file");
            }

            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException(
                        "Unsupported metadata format version: " + version);
            }

            int tableCount = input.readInt();
            requireCount(tableCount, MAX_TABLES, "table");

            for (int tableIndex = 0; tableIndex < tableCount; tableIndex++) {
                String tableName = readName(input, "table");
                String normalizedTableName = tableName.toLowerCase(
                        java.util.Locale.ROOT);

                if (!tableNames.add(normalizedTableName)) {
                    throw new IOException(
                            "Duplicate table in metadata: " + tableName);
                }

                int columnCount = input.readInt();
                requireCount(columnCount, MAX_COLUMNS, "column");
                if (columnCount == 0) {
                    throw new IOException(
                            "Table has no columns: " + tableName);
                }

                List<ColumnDefinition> columns = new ArrayList<>();
                Set<String> columnNames = new HashSet<>();

                for (int columnIndex = 0;
                     columnIndex < columnCount;
                     columnIndex++) {

                    String columnName = readName(input, "column");
                    String normalizedColumnName = columnName.toLowerCase(
                            java.util.Locale.ROOT);

                    if (!columnNames.add(normalizedColumnName)) {
                        throw new IOException(
                                "Duplicate column '" + columnName
                                        + "' in table '" + tableName + "'");
                    }

                    int typeCode = input.readUnsignedByte();
                    int varcharLength = input.readInt();
                    boolean primaryKey = input.readBoolean();

                    DataType dataType;
                    Integer length;

                    if (typeCode == 1) {
                        if (varcharLength != 0) {
                            throw new IOException(
                                    "Invalid INT column metadata: " + columnName);
                        }
                        dataType = DataType.INT;
                        length = null;
                    } else if (typeCode == 2) {
                        if (varcharLength <= 0) {
                            throw new IOException(
                                    "Invalid VARCHAR length: " + columnName);
                        }
                        dataType = DataType.VARCHAR;
                        length = varcharLength;
                    } else {
                        throw new IOException(
                                "Unknown column type code: " + typeCode);
                    }

                    columns.add(new ColumnDefinition(
                            columnName, dataType, length, primaryKey));
                }

                int pageCount = input.readInt();
                requireCount(pageCount, MAX_PAGES_PER_TABLE, "page");
                if (pageCount == 0) {
                    throw new IOException(
                            "Table has no data pages: " + tableName);
                }

                List<Long> pageIds = new ArrayList<>();
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                    long pageId = input.readLong();

                    if (pageId < 0) {
                        throw new IOException("Negative page ID in metadata");
                    }
                    if (!allPageIds.add(pageId)) {
                        throw new IOException(
                                "Page " + pageId + " is assigned more than once");
                    }

                    pageIds.add(pageId);
                }

                try {
                    new TableSchema(tableName, columns);
                    tables.add(new TableMetadata(tableName, columns, pageIds));
                } catch (IllegalArgumentException exception) {
                    throw new IOException(
                            "Invalid schema for table '" + tableName + "'",
                            exception);
                }
            }

            if (input.read() != -1) {
                throw new IOException(
                        "Unexpected trailing data in metadata file");
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid database metadata", exception);
        }

        return List.copyOf(tables);
    }

    private static String readName(DataInputStream input, String kind)
            throws IOException {
        String name = input.readUTF();

        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IOException("Invalid " + kind + " name in metadata");
        }

        return name.trim();
    }

    private static void requireCount(int count, int maximum, String kind)
            throws IOException {
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid " + kind + " count: " + count);
        }
    }
}
