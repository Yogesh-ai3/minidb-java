package minidb.storage;

import minidb.sql.SqlCommand.IntegerValue;
import minidb.sql.SqlCommand.SqlValue;
import minidb.sql.SqlCommand.StringValue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordPageTest {

    @Test
    void initializesEmptyPage() {
        byte[] page = RecordPage.initialize();

        assertEquals(0, RecordPage.getRecordCount(page));
        assertTrue(RecordPage.readRecords(page).isEmpty());
    }

    @Test
    void appendsAndReadsMultipleRecords() {
        byte[] page = RecordPage.initialize();

        List<SqlValue> first = List.of(
                new IntegerValue(1),
                new StringValue("Alice")
        );

        List<SqlValue> second = List.of(
                new IntegerValue(2),
                new StringValue("Bob")
        );

        assertTrue(RecordPage.append(page, first));
        assertTrue(RecordPage.append(page, second));

        assertEquals(2, RecordPage.getRecordCount(page));
        assertEquals(
                List.of(first, second),
                RecordPage.readRecords(page)
        );
    }

    @Test
    void reportsInsufficientSpaceWithoutCorruptingPage() {
        byte[] page = RecordPage.initialize();

        List<SqlValue> original = List.of(
                new IntegerValue(1),
                new StringValue("first")
        );

        assertTrue(RecordPage.append(page, original));

        byte[] before = page.clone();

        String largeString = "x".repeat(Page.PAGE_SIZE);

        assertFalse(RecordPage.append(
                page,
                List.of(new StringValue(largeString))
        ));

        assertArrayEquals(before, page);
        assertEquals(List.of(original), RecordPage.readRecords(page));
    }

    @Test
    void rejectsUninitializedPage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecordPage.readRecords(new byte[Page.PAGE_SIZE])
        );
    }

    @Test
    void rejectsIncorrectPageSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecordPage.readRecords(new byte[100])
        );
    }
}