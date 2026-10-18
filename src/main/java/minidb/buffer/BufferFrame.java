package minidb.buffer;

import minidb.storage.Page;

import java.util.Objects;

public final class BufferFrame {

    private final int frameId;
    private Page page;
    private boolean dirty;

    public BufferFrame(int frameId) {
        if (frameId < 0) {
            throw new IllegalArgumentException(
                    "Frame ID cannot be negative"
            );
        }

        this.frameId = frameId;
    }

    public int getFrameId() {
        return frameId;
    }

    public synchronized boolean isOccupied() {
        return page != null;
    }

    public synchronized Page getPage() {
        if (page == null) {
            throw new IllegalStateException("Buffer frame is empty");
        }

        return page;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void loadPage(Page page) {
        Objects.requireNonNull(page, "page");

        if (isOccupied()) {
            throw new IllegalStateException(
                    "Buffer frame is already occupied"
            );
        }

        this.page = page;
        this.dirty = false;
    }

    public synchronized void writeByte(int index, byte value) {
        requireOccupied();
        page.writeByte(index, value);
        dirty = true;
    }

    public synchronized void writeBytes(int offset, byte[] bytes) {
        requireOccupied();
        page.writeBytes(offset, bytes);
        dirty = true;
    }

    public synchronized void markDirty() {
        requireOccupied();
        dirty = true;
    }

    public synchronized void clearDirty() {
        dirty = false;
    }

    public synchronized void clear() {
        page = null;
        dirty = false;
    }

    private void requireOccupied() {
        if (page == null) {
            throw new IllegalStateException(
                    "Cannot modify an empty buffer frame"
            );
        }
    }
}