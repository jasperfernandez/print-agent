package com.jasperfernandez.printagent;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPrintEndpointTest {

    private static HttpServer server;
    private static int port;
    private static final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        PrinterService printerService = new PrinterService();
        PdfPrintService pdfPrintService = new PdfPrintService(printerService);

        server.createContext(
                "/print",
                exchange -> {
                    try {
                        var method = App.class.getDeclaredMethod(
                                "handlePrint",
                                com.sun.net.httpserver.HttpExchange.class,
                                PdfPrintService.class,
                                PrinterService.class);
                        method.setAccessible(true);
                        method.invoke(null, exchange, pdfPrintService, printerService);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rejectsNonPostMethod() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/print"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElse(null));
    }

    @Test
    void rejectsEmptyBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/print"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Request body must not be empty"));
    }

    @Test
    void rejectsMissingContent() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/print"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"printer\":\"any\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Field 'content' (base64-encoded PDF) is required."));
    }

    @Test
    void rejectsInvalidBase64Content() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/print"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"not-valid-base64!@#$\",\"printer\":\"any\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Invalid base64-encoded PDF content"));
    }

    @Test
    void rejectsPrinterNotFound() throws Exception {
        String base64Pdf = java.util.Base64.getEncoder().encodeToString("%PDF-1.4 sample".getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/print"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"content\":\"" + base64Pdf + "\",\"printer\":\"non-existent-printer-12345\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Printer not found"));
    }
}
