package gemini.workshop;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Server {

    private static Properties properties;

    static {
        properties = new Properties();
        try (InputStream input = Server.class.getClassLoader().getResourceAsStream("configuration.properties")) {
            if (input == null) {
                String errorMessage = "Unable to load configuration.properties";
                System.err.println(errorMessage);
                throw new RuntimeException(errorMessage);
            }
            properties.load(input);
        } catch (IOException ex) {
            System.err.println("Error loading configuration.properties: " + ex.getMessage());
            throw new RuntimeException("Error loading configuration.properties", ex);
        }
    }

    private static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static void main(String[] args) throws Exception {

        // RAG4.initialize(); // Load and ingest PDF data once
        Path pathToDelete_text = Paths.get(getProperty("output.text.file"));
        Path pathToDelete_excel = Paths.get(getProperty("output.excel.file"));
        Files.deleteIfExists(pathToDelete_text);
        Files.deleteIfExists(pathToDelete_excel);

        HttpServer server = HttpServer.create(new InetSocketAddress(8088), 0);
        server.createContext("/", new LoginHandler()); // Default context now goes to login
        server.createContext("/login", new LoginSubmitHandler());
        server.createContext("/home", new StaticFileHandler()); // Renamed context for the main page
        server.createContext("/ask", new AskHandler());
        server.createContext("/download/RAG_multiple.txt",
                new DownloadHandler(getProperty("output.text.file"), "text/plain", "RAG_multiple.txt")); // Existing text download
                                                                                                         
                                                                                                      
        server.createContext("/download/RAG_multiple.xlsx", new DownloadHandler(getProperty("output.excel.file"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "RAG_multiple.xlsx")); // New Excel download
                                                                                                            
        server.start();
        System.out.println("Server started at http://localhost:8088");

        // Automatically open the browser
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8088"));
        } else {
            System.out.println("Please open your browser and navigate to http://localhost:8088");
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("login.html");
            if (inputStream == null) {
                String error = "login.html not found!";
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            byte[] response = inputStream.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    static class LoginSubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);
                String username = params.get("username");
                String password = params.get("password");

                if (AuthenticationService.authenticateUser(username, password)) {
                    // Successful login, redirect to the main page
                    exchange.getResponseHeaders().set("Location", "/home");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    // Failed login, redirect back to the login page with an error message
                    String redirectUrl = "/?error=true";
                    exchange.getResponseHeaders().set("Location", redirectUrl);
                    exchange.sendResponseHeaders(302, -1);
                }
                exchange.getResponseBody().close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> map = new HashMap<>();
            String[] pairs = formData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    map.put(key, value);
                }
            }
            return map;
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("index.html");
            if (inputStream == null) {
                String error = "index.html not found!";
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            byte[] response = inputStream.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    static class AskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String encodedQuestion = requestBody.replace("question=", "");
                String question = URLDecoder.decode(encodedQuestion, StandardCharsets.UTF_8.name());

                String answer = "";
                try {
                    answer = DocumentQnA.askQuestion(question);
                } catch (Exception e) {
                    // Handle the exception appropriately, e.g., log it and return an error message
                    System.err.println("Error processing question: " + e.getMessage());
                    answer = "An error occurred while processing your question.";
                    exchange.sendResponseHeaders(500, answer.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(answer.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    return;
                }

                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                byte[] response = answer.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class DownloadHandler implements HttpHandler {
        private final String filePath;
        private final String contentType;
        private final String filename;

        public DownloadHandler(String filePath, String contentType, String filename) {
            this.filePath = filePath;
            this.contentType = contentType;
            this.filename = filename;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                String errorMessage = "File not found!";
                exchange.sendResponseHeaders(404, errorMessage.length());
                OutputStream os = exchange.getResponseBody();
                os.write(errorMessage.getBytes());
                os.close();
                return;
            }

            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            exchange.getResponseHeaders().add("Content-Type", contentType);
            long fileSize = file.length();
            exchange.sendResponseHeaders(200, fileSize);

            try (FileInputStream fis = new FileInputStream(file);
                    OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}