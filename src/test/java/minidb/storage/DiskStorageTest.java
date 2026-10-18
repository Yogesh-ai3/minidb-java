package minidb.storage;

import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void allocatesPagesSequentially() throws IOException {
        Path file = tempDir.resolve("database.db");

        try (DiskStorage storage = new DiskStorage(file)) {
            Page first = storage.allocatePage();
            Page second = storage.allocatePage();

            assertEquals(0, first.getPageId());
            assertEquals(1, second.getPageId());
            assertEquals(2, storage.getPageCount());

            assertArrayEquals(
                    new byte[Page.PAGE_SIZE],
                    first.getData()
            );
        }
    }

    @Test
    void writesAndReadsAllocatedPage() throws IOException {
        Path file = tempDir.resolve("database.db");
        byte[] data = new byte[Page.PAGE_SIZE];
        Arrays.fill(data, (byte) 7);

        try (DiskStorage storage = new DiskStorage(file)) {
            Page allocated = storage.allocatePage();

            storage.writePage(new Page(allocated.getPageId(), data));

            Page read = storage.readPage(allocated.getPageId());

            assertArrayEquals(data, read.getData());
            assertEquals(1, storage.getPageCount());
        }
    }

    @Test
    void dataPersistsAfterReopeningFile() throws IOException {
        Path file = tempDir.resolve("database.db");
        byte[] data = new byte[Page.PAGE_SIZE];
        Arrays.fill(data, (byte) 12);

        try (DiskStorage storage = new DiskStorage(file)) {
            Page page = storage.allocatePage();
            storage.writePage(new Page(page.getPageId(), data));
        }

        try (DiskStorage storage = new DiskStorage(file)) {
            assertArrayEquals(data, storage.readPage(0).getData());
            assertEquals(1, storage.getPageCount());
        }
    }

    @Test
    void storesPagesAtSeparateOffsets() throws IOException {
        Path file = tempDir.resolve("database.db");
        byte[] first = new byte[Page.PAGE_SIZE];
        byte[] second = new byte[Page.PAGE_SIZE];

        Arrays.fill(first, (byte) 1);
        Arrays.fill(second, (byte) 2);

        try (DiskStorage storage = new DiskStorage(file)) {
            Page page0 = storage.allocatePage();
            Page page1 = storage.allocatePage();

            storage.writePage(new Page(page0.getPageId(), first));
            storage.writePage(new Page(page1.getPageId(), second));

            assertArrayEquals(first, storage.readPage(0).getData());
            assertArrayEquals(second, storage.readPage(1).getData());
            assertEquals(2, storage.getPageCount());
        }
    }

    @Test
    void rejectsReadingUnallocatedPage() throws IOException {
        Path file = tempDir.resolve("database.db");

        try (DiskStorage storage = new DiskStorage(file)) {
            assertThrows(
                    IOException.class,
                    () -> storage.readPage(0)
            );
        }
    }

    @Test
    void rejectsWritingUnallocatedPage() throws IOException {
        Path file = tempDir.resolve("database.db");

        try (DiskStorage storage = new DiskStorage(file)) {
            Page page = new Page(0, new byte[Page.PAGE_SIZE]);

            assertThrows(
                    IOException.class,
                    () -> storage.writePage(page)
            );
        }
    }

    @Test
    void publishesEventsForRealStorageOperations()
            throws IOException {
        Path file = tempDir.resolve("database.db");
        SystemEventBus eventBus = new SystemEventBus();
        List<SystemEvent> events = new ArrayList<>();
        eventBus.addListener(events::add);

        try (DiskStorage storage = new DiskStorage(file, eventBus)) {
            Page page = storage.allocatePage();

            storage.writePage(
                    new Page(page.getPageId(), new byte[Page.PAGE_SIZE])
            );
            storage.readPage(page.getPageId());
        }

        List<EventType> types = events.stream()
                .map(SystemEvent::type)
                .toList();

        assertTrue(types.contains(EventType.FILE_UPDATED));
        assertTrue(types.contains(EventType.PAGE_WRITTEN));
        assertTrue(types.contains(EventType.FILE_READ));
        assertTrue(types.contains(EventType.PAGE_LOADED));
    }
}