
package com.jasperfernandez.printagent;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.print.PrintService;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

public final class PdfPrintService {

    private final PrinterService printerService;

    public PdfPrintService(PrinterService printerService) {
        this.printerService = printerService;
    }

    public void print(byte[] pdfBytes, String printerName)
            throws IOException, PrinterException {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content must not be null or empty.");
        }

        if (printerName == null || printerName.isBlank()) {
            throw new IllegalArgumentException("Printer name must not be blank.");
        }

        PrintService printer = printerService.findPrinter(printerName);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            validateDocument(document);

            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            job.setPageable(new PDFPageable(document));
            job.print();
        }
    }

    public void print(Path pdfPath, String printerName)
            throws IOException, PrinterException {

        validatePdf(pdfPath);

        if (printerName == null || printerName.isBlank()) {
            throw new IllegalArgumentException(
                    "Printer name must not be blank.");
        }

        PrintService printer = printerService.findPrinter(printerName);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            validateDocument(document);

            PrinterJob job = PrinterJob.getPrinterJob();

            job.setPrintService(printer);

            // Use the PDF's page dimensions rather than forcing
            // every document onto the default paper size.
            job.setPageable(new PDFPageable(document));

            // Submit directly to the selected printer without
            // displaying the standard print dialog.
            job.print();
        }
    }

    public void validatePdf(Path pdfPath) throws IOException {
        if (pdfPath == null) {
            throw new IllegalArgumentException("PDF path must not be null.");
        }

        if (!Files.isRegularFile(pdfPath) || !Files.isReadable(pdfPath)) {
            throw new IOException(
                    "PDF file does not exist or is not readable: " + pdfPath);
        }

        String fileName = pdfPath.getFileName() != null ? pdfPath.getFileName().toString().toLowerCase() : "";
        if (!fileName.endsWith(".pdf")) {
            throw new IllegalArgumentException("File must have a .pdf extension: " + pdfPath);
        }

        if (Files.size(pdfPath) == 0) {
            throw new IOException("PDF file is empty: " + pdfPath);
        }
    }

    private void validateDocument(PDDocument document) throws IOException {
        if (document.isEncrypted()) {
            throw new IOException("Encrypted PDF documents are not supported for printing.");
        }

        if (document.getNumberOfPages() == 0) {
            throw new IOException("PDF document contains no pages.");
        }
    }
}
