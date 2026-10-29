package com.jasperfernandez.printagent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;

/**
 * Print Agent App
 *
 */
public class App {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 17891;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(HOST, PORT),
                0);

        server.createContext("/health", App::handleHealth);
        server.setExecutor(Executors.newFixedThreadPool(4));

        PrinterService printerService = new PrinterService();
        PdfPrintService pdfPrintService = new PdfPrintService(printerService);

        server.createContext(
                "/printers",
                exchange -> handlePrinters(exchange, printerService));

        server.createContext(
                "/printers/default",
                exchange -> handleDefaultPrinter(exchange, printerService));

        server.createContext(
                "/print",
                exchange -> handlePrint(exchange, pdfPrintService, printerService));

        server.start();

        System.out.printf(
                "Print Agent running at http://%s:%d%n",
                HOST,
                PORT);
    }

    private static void handleHealth(HttpExchange exchange)
            throws IOException {

        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders()
                    .set("Allow", "GET");

            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String response = """
                {
                    "status": "ok",
                    "application": "print-agent",
                    "version": "1.0.0"
                }
                """;

        byte[] body = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(200, body.length);

        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void handlePrinters(
            HttpExchange exchange,
            PrinterService printerService) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String printers = printerService.getAvailablePrinters()
                .stream()
                .map(name -> "\"" + escapeJson(name) + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        sendJson(exchange, 200, "{\"printers\":[" + printers + "]}");
    }

    private static void handleDefaultPrinter(
            HttpExchange exchange,
            PrinterService printerService) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String printer = printerService.getDefaultPrinter();

        String response = printer == null
                ? "{\"printer\":null}"
                : "{\"printer\":\"" + escapeJson(printer) + "\"}";

        sendJson(exchange, 200, response);
    }

    private static void handlePrint(
            HttpExchange exchange,
            PdfPrintService pdfPrintService,
            PrinterService printerService) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        String body = new String(requestBytes, StandardCharsets.UTF_8).trim();

        if (body.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Request body must not be empty.\"}");
            return;
        }

        String printerName = extractJsonStringField(body, "printer");
        String base64Content = extractJsonStringField(body, "content");

        if (base64Content == null || base64Content.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Field 'content' (base64-encoded PDF) is required.\"}");
            return;
        }

        if (printerName == null || printerName.isBlank()) {
            printerName = printerService.getDefaultPrinter();
            if (printerName == null || printerName.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"No printer specified and no default printer found.\"}");
                return;
            }
        }

        byte[] pdfBytes;
        try {
            pdfBytes = Base64.getDecoder().decode(base64Content.trim());
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"Invalid base64-encoded PDF content.\"}");
            return;
        }

        try {
            pdfPrintService.print(pdfBytes, printerName);

            sendJson(exchange, 200, "{\"status\":\"success\",\"message\":\"Print job submitted.\",\"printer\":\""
                    + escapeJson(printerName) + "\"}");

        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (java.io.IOException e) {
            sendJson(exchange, 400, "{\"error\":\"PDF error: " + escapeJson(e.getMessage()) + "\"}");
        } catch (java.awt.print.PrinterException e) {
            sendJson(exchange, 500, "{\"error\":\"Printing error: " + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"Internal server error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String extractJsonStringField(String json, String field) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static String unescapeJson(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean inEscape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inEscape) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(c);
                }
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, body.length);

        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
