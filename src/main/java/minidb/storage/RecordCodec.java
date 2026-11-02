package minidb.storage;

import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.StringValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordCodec {

    private static final byte INTEGER_TYPE = 1;
    private static final byte STRING_TYPE = 2;

    private RecordCodec() {
    }

    public static byte[] encode(List<SqlValue> values) {
        Objects.requireNonNull(values, "values");

        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "A record must contain at least one value"
            );
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);

            output.writeInt(values.size());

            for (SqlValue value : values) {
                Objects.requireNonNull(value, "record value");

                if (value instanceof IntegerValue integerValue) {
                    output.writeByte(INTEGER_TYPE);
                    output.writeLong(integerValue.value());

                } else if (value instanceof StringValue stringValue) {
                    byte[] encodedString = stringValue.value()
                            .getBytes(StandardCharsets.UTF_8);

                    output.writeByte(STRING_TYPE);
                    output.writeInt(encodedString.length);
                    output.write(encodedString);

                } else {
                    throw new IllegalArgumentException(
                            "Unsupported record value type"
                    );
                }
            }

            output.flush();
            return bytes.toByteArray();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not encode record", exception
            );
        }
    }

    public static List<SqlValue> decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        try {
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(bytes)
            );

            int count = input.readInt();

            if (count <= 0 || count > input.available()) {
                throw new IllegalArgumentException(
                        "Invalid record value count"
                );
            }

            List<SqlValue> values = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                byte type = input.readByte();

                if (type == INTEGER_TYPE) {
                    values.add(new IntegerValue(input.readLong()));

                } else if (type == STRING_TYPE) {
                    int length = input.readInt();

                    if (length < 0 || length > input.available()) {
                        throw new IllegalArgumentException(
                                "Invalid encoded string length"
                        );
                    }

                    byte[] stringBytes = input.readNBytes(length);

                    values.add(new StringValue(
                            new String(stringBytes, StandardCharsets.UTF_8)
                    ));

                } else {
                    throw new IllegalArgumentException(
                            "Unknown record value type: " + type
                    );
                }
            }

            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Unexpected trailing bytes in record"
                );
            }

            return List.copyOf(values);

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Invalid encoded record", exception
            );
        }
    }
}