package minidb.storage;

import minidb.sql.SqlCommand.SqlValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordPage {

    private static final int MAGIC = 0x4D444250; // "MDBP"
    private static final int HEADER_SIZE = 12;

    private RecordPage() {
    }

    public static byte[] initialize() {
        byte[] data = new byte[Page.PAGE_SIZE];

        ByteBuffer header = ByteBuffer.wrap(data);
        header.putInt(MAGIC);
        header.putInt(0);           // Record count
        header.putInt(HEADER_SIZE); // Next free byte

        return data;
    }

    /**
     * Appends a record to the supplied page data.
     *
     * @return true if appended, false if there is insufficient space.
     */
    public static boolean append(
            byte[] pageData,
            List<SqlValue> values
    ) {
        validatePage(pageData);

        byte[] record = RecordCodec.encode(values);

        ByteBuffer header = ByteBuffer.wrap(pageData);

        int recordCount = header.getInt(4);
        int usedBytes = header.getInt(8);

        if (recordCount < 0
                || usedBytes < HEADER_SIZE
                || usedBytes > Page.PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid record page header"
            );
        }

        long requiredBytes = Integer.BYTES + (long) record.length;

        if (requiredBytes > Page.PAGE_SIZE - usedBytes) {
            return false;
        }

        // Write record length followed by encoded record.
        ByteBuffer buffer = ByteBuffer.wrap(pageData);
        buffer.position(usedBytes);
        buffer.putInt(record.length);
        buffer.put(record);

        // Update metadata after writing the record.
        buffer.putInt(4, recordCount + 1);
        buffer.putInt(8, usedBytes + (int) requiredBytes);

        return true;
    }

    public static List<List<SqlValue>> readRecords(byte[] pageData) {
        validatePage(pageData);

        ByteBuffer buffer = ByteBuffer.wrap(pageData);

        int recordCount = buffer.getInt(4);
        int usedBytes = buffer.getInt(8);

        if (recordCount < 0
                || usedBytes < HEADER_SIZE
                || usedBytes > Page.PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid record page header"
            );
        }

        List<List<SqlValue>> records = new ArrayList<>(recordCount);
        int position = HEADER_SIZE;

        for (int i = 0; i < recordCount; i++) {
            if (position > usedBytes - Integer.BYTES) {
                throw new IllegalArgumentException(
                        "Truncated record length"
                );
            }

            int recordLength = buffer.getInt(position);
            position += Integer.BYTES;

            if (recordLength <= 0 || recordLength > usedBytes - position) {
                throw new IllegalArgumentException(
                        "Invalid record length"
                );
            }

            byte[] encodedRecord = new byte[recordLength];
            buffer.position(position);
            buffer.get(encodedRecord);

            records.add(RecordCodec.decode(encodedRecord));
            position += recordLength;
        }

        if (position != usedBytes) {
            throw new IllegalArgumentException(
                    "Record count and used-byte metadata do not match"
            );
        }

        return List.copyOf(records);
    }

    public static int getRecordCount(byte[] pageData) {
        validatePage(pageData);
        return ByteBuffer.wrap(pageData).getInt(4);
    }

    private static void validatePage(byte[] pageData) {
        Objects.requireNonNull(pageData, "pageData");

        if (pageData.length != Page.PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page data must be exactly " + Page.PAGE_SIZE + " bytes"
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(pageData);

        if (buffer.getInt(0) != MAGIC) {
            throw new IllegalArgumentException(
                    "Page is not an initialized record page"
            );
        }
    }
}