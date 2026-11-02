package minidb.catalog;

import minidb.sql.SqlCommand.ColumnDefinition;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Catalog {
    private final Map<String, TableSchema> tables = new HashMap<>();

    public synchronized void createTable(
            String tableName,
            List<ColumnDefinition> columns
    ) {
        Objects.requireNonNull(tableName, "tableName");

        String key = normalizeName(tableName);


        if (tables.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Table already exists: " + tableName
            );
        }

        TableSchema schema = new TableSchema(key, columns);
        tables.put(key, schema);
    }

    public synchronized TableSchema getTable(String tableName) {
        Objects.requireNonNull(tableName, "tableName");

        String key = normalizeName(tableName);


        TableSchema schema = tables.get(key);

        if (schema == null) {
            throw new IllegalArgumentException(
                    "Table does not exist: " + tableName
            );
        }

        return schema;
    }

    public synchronized boolean tableExists(String tableName) {
        Objects.requireNonNull(tableName, "tableName");

        String key = normalizeName(tableName);

        return tables.containsKey(key);
    }

    public synchronized int getTableCount() {
        return tables.size();
    }

    public synchronized List<String> getTableNames() {
        return List.copyOf(tables.keySet());
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String key = name.trim().toLowerCase(Locale.ROOT);
    
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
    
        return key;
    }

    public synchronized void dropTable(String tableName) {
        String key = normalizeName(tableName);
    
        if (tables.remove(key) == null) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }
    }
}