
package com.jasperfernandez.printagent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class PrintIntegrationTest {

        private final PrinterService printerService = new PrinterService();

        private final PdfPrintService pdfPrintService = new PdfPrintService(printerService);

        @Test
        void shouldDiscoverAvailablePrinters() {
                List<String> printers = printerService.getAvailablePrinters();

                assertNotNull(printers);
                assertTrue(
                                printers.stream().allMatch(name -> !name.isBlank()),
                                "Printer names must not be blank.");
        }

        @Test
        @EnabledIfSystemProperty(named = "print.integration", matches = "true")
        void shouldSubmitReceiptToPrinter() throws Exception {
                String printerName = System.getProperty("printer.name");

                if (printerName == null || printerName.isBlank()) {
                        printerName = printerService.getDefaultPrinter();
                }

                assertNotNull(
                                printerName,
                                "No printer specified and no default printer is configured.");

                Path receiptPath = getReceiptPath();

                System.out.println("Selected printer: " + printerName);
                System.out.println("Receipt: " + receiptPath);

                String selectedPrinter = printerName;

                assertDoesNotThrow(
                                () -> pdfPrintService.print(receiptPath, selectedPrinter),
                                "The receipt should be submitted without an exception.");
        }

        private Path getReceiptPath() throws URISyntaxException {
                var resource = getClass().getResource("/receipt.pdf");

                assertNotNull(
                                resource,
                                "Place receipt.pdf inside src/test/resources.");

                return Path.of(resource.toURI());
        }
}
