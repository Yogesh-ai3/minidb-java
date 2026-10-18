package minidb.storage;

import java.util.Arrays;
import java.util.Objects;

public final class Page {

    public static final int PAGE_SIZE = 4096;

    private final long pageId;
    private final byte[] data;

    public Page(long pageId, byte[] data) {
        if (pageId < 0) {
            throw new IllegalArgumentException("Page ID cannot be negative");
        }

        if (data == null || data.length != PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page data must be exactly " + PAGE_SIZE + " bytes"
            );
        }

        this.pageId = pageId;
        this.data = Arrays.copyOf(data, data.length);
    }

    public long getPageId() {
        return pageId;
    }

    public synchronized byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public synchronized void writeByte(int index, byte value) {
        if (index < 0 || index >= PAGE_SIZE) {
            throw new IndexOutOfBoundsException(
                    "Byte index must be between 0 and " + (PAGE_SIZE - 1)
            );
        }

        data[index] = value;
    }

    public synchronized void writeBytes(int offset, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        if (offset < 0 || offset > PAGE_SIZE - bytes.length) {
            throw new IndexOutOfBoundsException(
                    "Write exceeds page boundaries"
            );
        }

        System.arraycopy(bytes, 0, data, offset, bytes.length);
    }
}