package com.jasperfernandez.printagent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfPrintServiceTest {

    @TempDir
    Path tempDir;

    private final PdfPrintService service =
            new PdfPrintService(new PrinterService());

    @Test
    void throwsWhenPdfPathIsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.print((Path) null, "any"));
    }

    @Test
    void throwsWhenPdfBytesAreNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.print((byte[]) null, "any"));
        assertThrows(IllegalArgumentException.class, () -> service.print(new byte[0], "any"));
    }

    @Test
    void throwsWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("missing.pdf");
        assertThrows(IOException.class, () -> service.print(missing, "any"));
    }

    @Test
    void throwsWhenPathIsDirectory() {
        assertThrows(IOException.class, () -> service.print(tempDir, "any"));
    }

    @Test
    void throwsWhenNotPdfExtension() throws IOException {
        Path txt = Files.writeString(tempDir.resolve("sample.txt"), "hello");
        assertThrows(IllegalArgumentException.class, () -> service.print(txt, "any"));
    }

    @Test
    void throwsWhenPdfIsEmpty() throws IOException {
        Path empty = Files.createFile(tempDir.resolve("empty.pdf"));
        assertThrows(IOException.class, () -> service.print(empty, "any"));
    }

    @Test
    void validatePdfDirectly() throws IOException {
        Path valid = Files.writeString(tempDir.resolve("sample.pdf"), "%PDF-1.4");
        assertDoesNotThrow(() -> service.validatePdf(valid));

        assertThrows(IllegalArgumentException.class, () -> service.validatePdf(null));

        Path nonPdf = Files.writeString(tempDir.resolve("sample.png"), "image-content");
        assertThrows(IllegalArgumentException.class, () -> service.validatePdf(nonPdf));
    }

    @Test
    void throwsWhenPrinterNameIsNull() throws IOException {
        Path pdf = Files.writeString(tempDir.resolve("a.pdf"), "%PDF-1.4");
        assertThrows(IllegalArgumentException.class,
                () -> service.print(pdf, null));
    }

    @Test
    void throwsWhenPrinterNameIsBlank() throws IOException {
        Path pdf = Files.writeString(tempDir.resolve("a.pdf"), "%PDF-1.4");
        assertThrows(IllegalArgumentException.class,
                () -> service.print(pdf, "   "));
    }

    @Test
    void throwsWhenPrinterNotFound() throws IOException {
        Path pdf = Files.writeString(tempDir.resolve("a.pdf"), "%PDF-1.4");
        assertThrows(IllegalArgumentException.class,
                () -> service.print(pdf, "no-such-printer-xyz"));
    }
}
