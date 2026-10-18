package minidb.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void createsPageWithFixedSizeData() {
        byte[] data = new byte[Page.PAGE_SIZE];

        Page page = new Page(0, data);

        assertEquals(0, page.getPageId());
        assertEquals(Page.PAGE_SIZE, page.getData().length);
    }

    @Test
    void rejectsNegativePageId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Page(-1, new byte[Page.PAGE_SIZE])
        );
    }

    @Test
    void rejectsIncorrectPageSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Page(0, new byte[10])
        );
    }

    @Test
    void protectsItsInternalData() {
        byte[] original = new byte[Page.PAGE_SIZE];
        Page page = new Page(0, original);

        original[0] = 42;
        assertEquals(0, page.getData()[0]);

        byte[] returned = page.getData();
        returned[0] = 99;
        assertEquals(0, page.getData()[0]);
    }
}