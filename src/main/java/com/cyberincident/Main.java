
package com.cyberincident;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    static class Incident {
        int id;
        String title;
        String severity;
        String description;

        Incident(int id, String title, String severity,
                 String description) {
            this.id = id;
            this.title = title;
            this.severity = severity;
            this.description = description;
        }
    }

    static class Action {
        int id;
        String text;

        Action(int id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private static final AtomicInteger incidentIds =
            new AtomicInteger(1);
    private static final AtomicInteger actionIds =
            new AtomicInteger(1);

    private static final PriorityQueue<Incident> incidents =
            new PriorityQueue<>(
                    Comparator.comparingInt(
                            (Incident i) -> severityRank(i.severity))
                            .thenComparingInt(i -> i.id));

    private static final Deque<Action> actions = new ArrayDeque<>();
    private static final Object lock = new Object();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 8080), 0);

        server.createContext("/api/incidents", exchange -> {
            String method = exchange.getRequestMethod();

            if ("GET".equals(method)) {
                synchronized (lock) {
                    sendJson(exchange, 200, incidentsJson());
                }
            } else if ("POST".equals(method)) {
                String body = readBody(exchange);
                String title = getValue(body, "title");
                String severity = getValue(body, "severity");
                String description = getValue(body, "description");

                if (title.isBlank()) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Incident title is required\"}");
                    return;
                }

                if (severityRank(severity) == 99) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Invalid severity\"}");
                    return;
                }

                synchronized (lock) {
                    incidents.offer(new Incident(
                            incidentIds.getAndIncrement(),
                            title, severity, description));
                    sendJson(exchange, 201, incidentsJson());
                }
            } else {
                sendJson(exchange, 405,
                        "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/process", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405,
                        "{\"error\":\"Method not allowed\"}");
                return;
            }

            synchronized (lock) {
                Incident incident = incidents.poll();

                if (incident == null) {
                    sendJson(exchange, 200,
                            "{\"message\":\"No incidents to process\"}");
                } else {
                    String message = "Processed incident: "
                            + incident.title + " (" + incident.severity + ")";

                    actions.push(new Action(
                            actionIds.getAndIncrement(), message));

                    sendJson(exchange, 200,
                            "{\"message\":\"" + escape(message) + "\"}");
                }
            }
        });

        server.createContext("/api/actions", exchange -> {
            String method = exchange.getRequestMethod();

            if ("GET".equals(method)) {
                synchronized (lock) {
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;

                    for (Action action : actions) {
                        if (!first) {
                            json.append(",");
                        }
                        first = false;

                        json.append("{\"id\":")
                                .append(action.id)
                                .append(",\"text\":\"")
                                .append(escape(action.text))
                                .append("\"}");
                    }

                    json.append("]");
                    sendJson(exchange, 200, json.toString());
                }

            } else if ("POST".equals(method)) {
                String body = readBody(exchange);
                String text = getValue(body, "text");

                if (text.isBlank()) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Action text is required\"}");
                    return;
                }

                synchronized (lock) {
                    actions.push(new Action(
                            actionIds.getAndIncrement(), text));

                    sendJson(exchange, 201,
                            "{\"message\":\"Response action saved\"}");
                }

            } else {
                sendJson(exchange, 405,
                        "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/undo", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405,
                        "{\"error\":\"Method not allowed\"}");
                return;
            }

            synchronized (lock) {
                if (actions.isEmpty()) {
                    sendJson(exchange, 200,
                            "{\"message\":\"No actions to undo\"}");
                } else {
                    Action action = actions.pop();
                    sendJson(exchange, 200,
                            "{\"message\":\"Undid: "
                                    + escape(action.text) + "\"}");
                }
            }
        });

        server.createContext("/api/reset", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405,
                        "{\"error\":\"Method not allowed\"}");
                return;
            }

            synchronized (lock) {
                incidents.clear();
                actions.clear();
                incidentIds.set(1);
                actionIds.set(1);

                sendJson(exchange, 200,
                        "{\"message\":\"System reset successfully\"}");
            }
        });

        server.createContext("/", Main::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Cyber Incident Response System is running!");
        System.out.println("Open http://127.0.0.1:8080");
    }

    private static int severityRank(String severity) {
        if (severity == null) {
            return 99;
        }

        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return 1;
            case "HIGH":
                return 2;
            case "MEDIUM":
                return 3;
            case "LOW":
                return 4;
            default:
                return 99;
        }
    }

    private static String incidentsJson() {
        List<Incident> list = new ArrayList<>(incidents);
        list.sort(
                Comparator.comparingInt(
                        (Incident i) -> severityRank(i.severity))
                        .thenComparingInt(i -> i.id));

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Incident incident : list) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("{\"id\":").append(incident.id)
                    .append(",\"title\":\"")
                    .append(escape(incident.title))
                    .append("\",\"severity\":\"")
                    .append(escape(incident.severity))
                    .append("\",\"description\":\"")
                    .append(escape(incident.description))
                    .append("\"}");
        }

        return json.append("]").toString();
    }

    private static String readBody(HttpExchange exchange)
            throws IOException {
        return new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static String getValue(String body, String key) {
        String pattern = "\"" + key + "\"";
        int keyPosition = body.indexOf(pattern);

        if (keyPosition < 0) {
            return "";
        }

        int colon = body.indexOf(":", keyPosition);
        if (colon < 0) {
            return "";
        }

        int firstQuote = body.indexOf("\"", colon + 1);
        if (firstQuote < 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        for (int i = firstQuote + 1; i < body.length(); i++) {
            char c = body.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    default: value.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
        }

        return value.toString().trim();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void sendJson(
            HttpExchange exchange, int status, String response)
            throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void serveStatic(HttpExchange exchange)
            throws IOException {
        String requested = exchange.getRequestURI().getPath();

        if (requested.equals("/")) {
            requested = "/index.html";
        }

        Path root = Path.of("out", "public").toAbsolutePath().normalize();
        Path file = root.resolve(requested.substring(1)).normalize();

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] bytes = "Not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            return;
        }

        String name = file.getFileName().toString();
        String contentType = "text/plain; charset=UTF-8";

        if (name.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (name.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (name.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
