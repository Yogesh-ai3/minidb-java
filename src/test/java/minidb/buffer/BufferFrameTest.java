package minidb.buffer;

import minidb.storage.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BufferFrameTest {

    @Test
    void startsEmptyAndLoadsPage() {
        BufferFrame frame = new BufferFrame(0);
        Page page = new Page(5, new byte[Page.PAGE_SIZE]);

        assertFalse(frame.isOccupied());

        frame.loadPage(page);

        assertTrue(frame.isOccupied());
        assertEquals(0, frame.getFrameId());
        assertEquals(5, frame.getPage().getPageId());
        assertFalse(frame.isDirty());
    }

    @Test
    void rejectsLoadingIntoOccupiedFrame() {
        BufferFrame frame = new BufferFrame(0);

        frame.loadPage(new Page(1, new byte[Page.PAGE_SIZE]));

        assertThrows(
                IllegalStateException.class,
                () -> frame.loadPage(
                        new Page(2, new byte[Page.PAGE_SIZE])
                )
        );
    }

    @Test
    void tracksAndClearsDirtyState() {
        BufferFrame frame = new BufferFrame(0);

        assertThrows(
                IllegalStateException.class,
                frame::markDirty
        );

        frame.loadPage(new Page(1, new byte[Page.PAGE_SIZE]));
        frame.markDirty();

        assertTrue(frame.isDirty());

        frame.clearDirty();

        assertFalse(frame.isDirty());
    }

    @Test
    void clearingFrameReleasesItsPage() {
        BufferFrame frame = new BufferFrame(0);
        frame.loadPage(new Page(1, new byte[Page.PAGE_SIZE]));
        frame.markDirty();

        frame.clear();

        assertFalse(frame.isOccupied());
        assertFalse(frame.isDirty());
        assertThrows(IllegalStateException.class, frame::getPage);
    }

    @Test
    void rejectsNegativeFrameId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BufferFrame(-1)
        );
    }
}