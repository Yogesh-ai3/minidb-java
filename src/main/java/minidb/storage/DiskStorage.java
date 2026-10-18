package minidb.storage;

import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Objects;

public final class DiskStorage implements AutoCloseable {

    private final RandomAccessFile file;
    private final SystemEventBus eventBus;

    public DiskStorage(Path filePath) throws IOException {
        this(filePath, null);
    }

    public DiskStorage(
            Path filePath,
            SystemEventBus eventBus
    ) throws IOException {
        Objects.requireNonNull(filePath, "filePath");
        this.file = new RandomAccessFile(filePath.toFile(), "rw");
        this.eventBus = eventBus;
    }

    public synchronized Page allocatePage() throws IOException {
        long pageCount = getPageCount();
        long offset = file.length();

        byte[] emptyData = new byte[Page.PAGE_SIZE];
        file.seek(offset);
        file.write(emptyData);

        Page page = new Page(pageCount, emptyData);

        publish(EventType.FILE_UPDATED,
                "Allocated page " + pageCount + " on disk");
        publish(EventType.PAGE_WRITTEN,
                "Initialized page " + pageCount);

        return page;
    }

    public synchronized void writePage(Page page) throws IOException {
        Objects.requireNonNull(page, "page");

        validateAllocatedPage(page.getPageId());

        long offset = pageOffset(page.getPageId());
        file.seek(offset);
        file.write(page.getData());

        publish(EventType.FILE_UPDATED,
                "Updated disk file for page " + page.getPageId());
        publish(EventType.PAGE_WRITTEN,
                "Wrote page " + page.getPageId());
    }

    public synchronized Page readPage(long pageId) throws IOException {
        validateAllocatedPage(pageId);

        byte[] data = new byte[Page.PAGE_SIZE];

        file.seek(pageOffset(pageId));
        file.readFully(data);

        publish(EventType.FILE_READ,
                "Read disk file for page " + pageId);
        publish(EventType.PAGE_LOADED,
                "Loaded page " + pageId + " from disk");

        return new Page(pageId, data);
    }

    public synchronized long getPageCount() throws IOException {
        long length = file.length();

        if (length % Page.PAGE_SIZE != 0) {
            throw new IOException(
                    "Database file has an incomplete page"
            );
        }

        return length / Page.PAGE_SIZE;
    }

    private void validateAllocatedPage(long pageId)
            throws IOException {
        if (pageId < 0) {
            throw new IOException("Page ID cannot be negative");
        }

        long pageCount = getPageCount();

        if (pageId >= pageCount) {
            throw new IOException(
                    "Page " + pageId + " has not been allocated"
            );
        }
    }

    private long pageOffset(long pageId) throws IOException {
        try {
            return Math.multiplyExact(
                    pageId, (long) Page.PAGE_SIZE
            );
        } catch (ArithmeticException exception) {
            throw new IOException("Page offset is too large", exception);
        }
    }

    private void publish(EventType type, String message) {
        if (eventBus != null) {
            eventBus.publish(new SystemEvent(type, message));
        }
    }

    @Override
    public synchronized void close() throws IOException {
        file.close();
    }
}