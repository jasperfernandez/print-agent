package com.jasperfernandez.printagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class PrinterServiceTest {

    private final PrinterService service = new PrinterService();

    @Test
    void getAvailablePrintersReturnsNonNullList() {
        List<String> printers = service.getAvailablePrinters();
        assertNotNull(printers);
    }

    @Test
    void getDefaultPrinterIsNullOrListedPrinter() {
        String def = service.getDefaultPrinter();
        if (def != null) {
            assertTrue(service.getAvailablePrinters().contains(def));
        }
    }

    @Test
    void findPrinterThrowsForUnknownPrinter() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.findPrinter("no-such-printer-xyz"));
        assertTrue(ex.getMessage().contains("no-such-printer-xyz"));
    }

    @Test
    void findPrinterReturnsAvailablePrinter() {
        for (String name : service.getAvailablePrinters()) {
            assertEquals(name, service.findPrinter(name).getName());
        }
    }
}
