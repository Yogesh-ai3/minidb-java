package minidb.storage;

import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordCodecTest {

    @Test
    void roundTripsIntegerAndStringValues() {
        List<SqlValue> original = List.of(
                new IntegerValue(42),
                new StringValue("Alice"),
                new StringValue("O'Brien")
        );

        byte[] encoded = RecordCodec.encode(original);

        assertEquals(original, RecordCodec.decode(encoded));
    }

    @Test
    void roundTripsEmptyStringAndLargeInteger() {
        List<SqlValue> original = List.of(
                new IntegerValue(Long.MAX_VALUE),
                new StringValue("")
        );

        assertEquals(original, RecordCodec.decode(
                RecordCodec.encode(original)
        ));
    }

    @Test
    void rejectsEmptyRecord() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.encode(List.of())
        );
    }

    @Test
    void rejectsUnknownValueTypeMarker() {
        byte[] invalid = {
                0, 0, 0, 1,
                99
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.decode(invalid)
        );
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] encoded = RecordCodec.encode(
                List.of(new IntegerValue(7))
        );

        byte[] invalid = java.util.Arrays.copyOf(
                encoded, encoded.length + 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RecordCodec.decode(invalid)
        );
    }
}