
package com.jasperfernandez.printagent;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.Arrays;
import java.util.List;

public final class PrinterService {

    public List<String> getAvailablePrinters() {
        return Arrays.stream(
                PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted()
                .toList();
    }

    public String getDefaultPrinter() {
        PrintService printer = PrintServiceLookup.lookupDefaultPrintService();

        return printer != null ? printer.getName() : null;
    }

    public PrintService findPrinter(String printerName) {
        return Arrays.stream(
                PrintServiceLookup.lookupPrintServices(null, null))
                .filter(service -> service.getName().equals(printerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Printer not found: " + printerName));
    }
}
