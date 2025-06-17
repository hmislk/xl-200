package org.carecode.mw.lims.mw.xl200;

import org.carecode.lims.libraries.QueryRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XL200ParsersTest {
    @Test
    void parseQueryRecord_extractsSampleIdAfterCaret() {
        QueryRecord record = XL200Parsers.parseQueryRecord("Q|1|^1857128");
        assertEquals("1857128", record.getSampleId());
    }
}
