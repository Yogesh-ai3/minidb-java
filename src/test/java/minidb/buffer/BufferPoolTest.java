package minidb.buffer;

import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;
import minidb.storage.DiskStorage;
import minidb.storage.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAllocatedPageFromDiskOnCacheMiss() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            Page page = storage.allocatePage();
            BufferPool pool = new BufferPool(2, storage, null);

            BufferFrame frame = pool.getPage(page.getPageId());

            assertEquals(page.getPageId(), frame.getPage().getPageId());
            assertEquals(1, pool.getOccupiedFrameCount());
        }
    }

    @Test
    void reusesSameFrameOnCacheHit() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            Page page = storage.allocatePage();
            BufferPool pool = new BufferPool(2, storage, null);

            BufferFrame first = pool.getPage(page.getPageId());
            BufferFrame second = pool.getPage(page.getPageId());

            assertSame(first, second);
            assertEquals(1, pool.getOccupiedFrameCount());
        }
    }

    @Test
    void evictsLeastRecentlyUsedPage() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            storage.allocatePage(); // Page 0
            storage.allocatePage(); // Page 1
            storage.allocatePage(); // Page 2

            BufferPool pool = new BufferPool(2, storage, null);

            pool.getPage(0);
            pool.getPage(1);
            pool.getPage(0); // Page 0 becomes most recently used.
            pool.getPage(2); // Page 1 should be evicted.

            assertNotNull(pool.findFrameByPageId(0));
            assertNull(pool.findFrameByPageId(1));
            assertNotNull(pool.findFrameByPageId(2));
            assertEquals(2, pool.getOccupiedFrameCount());
        }
    }

    @Test
    void reloadedEvictedPageRetainsDiskContents() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            Page page0 = storage.allocatePage();
            storage.allocatePage();
            Page page2 = storage.allocatePage();

            byte[] expected = new byte[Page.PAGE_SIZE];
            expected[0] = 42;
            storage.writePage(new Page(page2.getPageId(), expected));

            BufferPool pool = new BufferPool(2, storage, null);

            pool.getPage(page0.getPageId());
            pool.getPage(1);
            pool.getPage(2); // Evicts page 0.

            BufferFrame reloaded = pool.getPage(0);

            assertEquals(0, reloaded.getPage().getPageId());
            assertEquals(2, pool.getOccupiedFrameCount());

            // Reload page 2 separately to verify its stored bytes.
            pool.getPage(2);
            assertEquals(42, pool.findFrameByPageId(2)
                    .getPage().getData()[0]);
        }
    }

    @Test
    void dirtyPageIsWrittenBeforeEviction() throws IOException {
        Path file = tempDir.resolve("database.db");
        SystemEventBus eventBus = new SystemEventBus();
        List<SystemEvent> events = new ArrayList<>();
        eventBus.addListener(events::add);

        try (DiskStorage storage = new DiskStorage(file, eventBus)) {
            storage.allocatePage();
            storage.allocatePage();

            BufferPool pool = new BufferPool(1, storage, eventBus);

            BufferFrame frame = pool.getPage(0);
            frame.markDirty();

            pool.getPage(1); // Evicts dirty page 0.

            assertNull(pool.findFrameByPageId(0));
            assertNotNull(pool.findFrameByPageId(1));

            assertTrue(events.stream().anyMatch(
                    event -> event.type() == EventType.PAGE_WRITTEN
            ));
            assertTrue(events.stream().anyMatch(
                    event -> event.type() == EventType.BUFFER_FRAME_EVICTED
            ));
        }
    }

    @Test
    void invalidPageDoesNotEvictCachedPage() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            storage.allocatePage();

            BufferPool pool = new BufferPool(1, storage, null);
            pool.getPage(0);

            assertThrows(IOException.class, () -> pool.getPage(99));

            assertNotNull(pool.findFrameByPageId(0));
            assertEquals(1, pool.getOccupiedFrameCount());
        }
    }

    @Test
    void rejectsNonPositiveCapacity() throws IOException {
        try (DiskStorage storage =
                     new DiskStorage(tempDir.resolve("database.db"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BufferPool(0, storage, null)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BufferPool(-1, storage, null)
            );
        }
    }
}