package minidb.buffer;

import minidb.monitoring.EventType;
import minidb.monitoring.SystemEvent;
import minidb.monitoring.SystemEventBus;
import minidb.storage.DiskStorage;
import minidb.storage.Page;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BufferPool {

    private final List<BufferFrame> frames;
    private final LinkedHashMap<Long, BufferFrame> framesByPage;
    private final DiskStorage storage;
    private final SystemEventBus eventBus;

    public BufferPool(
            int capacity,
            DiskStorage storage,
            SystemEventBus eventBus
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Buffer pool capacity must be positive"
            );
        }

        this.storage = Objects.requireNonNull(storage, "storage");
        this.eventBus = eventBus;
        this.frames = new ArrayList<>(capacity);
        this.framesByPage = new LinkedHashMap<>(capacity, 0.75f, true);

        for (int i = 0; i < capacity; i++) {
            frames.add(new BufferFrame(i));
        }
    }

    public int getCapacity() {
        return frames.size();
    }

    public synchronized int getOccupiedFrameCount() {
        return framesByPage.size();
    }

    public synchronized BufferFrame getPage(long pageId)
            throws IOException {

        BufferFrame cached = framesByPage.get(pageId);

        if (cached != null) {
            return cached;
        }

        // Read first so an invalid page cannot evict a valid cached page.
        Page page = storage.readPage(pageId);

        BufferFrame frame = findFreeFrame();

        if (frame == null) {
            frame = evictLeastRecentlyUsed();
        }

        frame.loadPage(page);
        framesByPage.put(pageId, frame);

        publish(
                EventType.BUFFER_FRAME_ACQUIRED,
                "Loaded page " + pageId
                        + " into buffer frame " + frame.getFrameId()
        );

        return frame;
    }

    /**
     * Looks up a cached page without changing its LRU position.
     */
    public synchronized BufferFrame findFrameByPageId(long pageId) {
        for (Map.Entry<Long, BufferFrame> entry
                : framesByPage.entrySet()) {
            if (entry.getKey() == pageId) {
                return entry.getValue();
            }
        }

        return null;
    }

    public synchronized BufferFrame findFreeFrame() {
        for (BufferFrame frame : frames) {
            if (!frame.isOccupied()) {
                return frame;
            }
        }

        return null;
    }

    /**
     * Writes a cached dirty page to disk.
     * Does nothing if the page is not currently cached.
     */
    public synchronized void flushPage(long pageId) throws IOException {
        BufferFrame frame = findFrameByPageId(pageId);

        if (frame != null) {
            flushFrame(frame);
        }
    }

    /**
     * Writes every dirty cached page to disk.
     */
    public synchronized void flushAll() throws IOException {
        for (BufferFrame frame : frames) {
            if (frame.isOccupied() && frame.isDirty()) {
                flushFrame(frame);
            }
        }
    }

    private void flushFrame(BufferFrame frame) throws IOException {
        if (!frame.isOccupied() || !frame.isDirty()) {
            return;
        }

        // Clear dirty only after the disk write succeeds.
        storage.writePage(frame.getPage());
        frame.clearDirty();
    }

    private BufferFrame evictLeastRecentlyUsed() throws IOException {
        Iterator<Map.Entry<Long, BufferFrame>> iterator =
                framesByPage.entrySet().iterator();

        if (!iterator.hasNext()) {
            throw new IllegalStateException(
                    "No buffer frame available for eviction"
            );
        }

        Map.Entry<Long, BufferFrame> eldest = iterator.next();
        long pageId = eldest.getKey();
        BufferFrame frame = eldest.getValue();

        flushFrame(frame);

        iterator.remove();
        frame.clear();

        publish(
                EventType.BUFFER_FRAME_EVICTED,
                "Evicted page " + pageId
                        + " from buffer frame " + frame.getFrameId()
        );

        return frame;
    }

    private void publish(EventType type, String message) {
        if (eventBus != null) {
            eventBus.publish(new SystemEvent(type, message));
        }
    }
}