package com.vamshikrishna;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class HttpServerAppIdeaCombinedCode {
    private static final String HTTP_NEW_LINE_DELIMITER = "\r\n";
    private static final String HTTP_HEAD_BODY_DELIMITER = HTTP_NEW_LINE_DELIMITER + HTTP_NEW_LINE_DELIMITER;
    private static final int HTTP_HEAD_BODY_SEPARTOR_BYTES = HTTP_NEW_LINE_DELIMITER.getBytes(StandardCharsets.US_ASCII).length;
    private static final int DEFAULT_BUFFER_SIZE = 10_000;
    private static final String CONTENT_LENGTH_HEADER = "content-length";
    private static final String CONNECTION_HEADER = "connection";
    private static final String CONNECTION_HEADER_KEEP_ALIVE = "keep-alive";

    public static void main(String[] args) throws Exception {
        var serverSocket = new ServerSocket(8080);
        var executor = Executors.newFixedThreadPool(10);
        var connections = 0;
        while(true) {
            var connection = serverSocket.accept();
            connections++;
            System.out.println("Number of total connections -> " + connections);
            connection.setSoTimeout(10_000);
            executor.execute(() -> handleRequest(connection)); // to handle multiple client requests without blocking the server
        }
    }

    private static Optional<HttpRequest> readRequest(Socket connection) throws Exception {
        var stream = connection.getInputStream();
        var rawRequestHead = readRawRequestHeader(stream);
        if(rawRequestHead.length == 0) return Optional.empty();
        var requestHead = new String(rawRequestHead, StandardCharsets.US_ASCII);
        var lines = requestHead.split(HTTP_NEW_LINE_DELIMITER);
        var line = lines[0];
        var methodUrl = line.split(" ");
        var method = methodUrl[0];
        var url = methodUrl[1];
        var headers = readHeaders(lines);
        var bodyLength = getExpectedBodyLength(headers);
        byte[] body;
        if(bodyLength > 0) {
            var bodyStartIdx = requestHead.indexOf(HTTP_HEAD_BODY_DELIMITER);
            if(bodyStartIdx > 0) {
                var readBody = Arrays.copyOfRange(rawRequestHead, bodyStartIdx + HTTP_HEAD_BODY_SEPARTOR_BYTES, rawRequestHead.length);
                body = readBody(stream, readBody, bodyLength);
            } else {
                body = new byte[0];
            }
        }
        else {
            body = new byte[0];
        }
        return Optional.of(new HttpRequest(method, url, headers, body));
    }

    private static int getExpectedBodyLength(Map<String, List<String>> headers) {
        try {
            return Integer.parseInt(headers.getOrDefault(CONTENT_LENGTH_HEADER, List.of("0")).get(0));
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] readRawRequestHeader(InputStream stream) throws Exception {
        var toRead = stream.available();
        if(toRead == 0) toRead = DEFAULT_BUFFER_SIZE;
        var buffer = new byte[toRead];
        var read = stream.read(buffer);
        if(read <= 0) return new byte[0];
        return read == toRead ? buffer : Arrays.copyOf(buffer, read);
    }

    private static void handleRequest(Socket connection) {
        try {
            var requestOpt = readRequest(connection);
            if (requestOpt.isEmpty()) {
                closeConnection(connection);
                return;
            }
            var request = requestOpt.get();
            PrintRequest(request);
            respondToRequest(connection, request);
            if (shouldReuseConnection(request.headers())) {
                // System.out.println("Reusing the connection...");
                handleRequest(connection);
            }
        }
        catch (SocketTimeoutException e) {
            System.out.println("Socket timeout, closing..");
            closeConnection(connection);
        } catch (Exception e) {
            System.out.println("Problem while handling connection");
            e.printStackTrace();
            closeConnection(connection);
        }
    }

    private static void respondToRequest(Socket connection, HttpRequest req) throws Exception {
        var res = requestResponse(req);
        var os = connection.getOutputStream();
        var resHead = new StringBuilder("HTTP/1.1 %d".formatted(res.responseCode()));
        res.headers().forEach((k, vs) ->
                vs.forEach(v ->
                        resHead.append(HTTP_NEW_LINE_DELIMITER)
                                .append(k)
                                .append(": ")
                                .append(v)));
        resHead.append(HTTP_HEAD_BODY_DELIMITER);
        os.write(resHead.toString().getBytes(StandardCharsets.US_ASCII));
        if(res.body().length > 0) {
            os.write(res.body());
        }
    }
    private static HttpResponse requestResponse(HttpRequest req) {
        var body = """
                    {
                        "id": 1
                    }
                    """.getBytes(StandardCharsets.UTF_8);

//        var response = new StringBuilder()
//                .append("HTTP/1.1 200 OK")
//                .append(HTTP_NEW_LINE_DELIMITER)
//                .append("Content-Type: application/json")
//                .append(HTTP_NEW_LINE_DELIMITER)
//                .append("Content-Length: %d".formatted(body.getBytes(StandardCharsets.UTF_8).length))
//                .append(HTTP_NEW_LINE_DELIMITER)
//                .append(HTTP_HEAD_BODY_DELIMITER)
//                .append(body)
//                .toString();

//                    var response = """
//                    HTTP/1.1 200 OK
//                    Content-Type: application/json
//                    Content-Length: %d
//
//                    %s
//                    """.formatted(body.getBytes(StandardCharsets.UTF_8).length, body); // no reuse of permanent connections as the response is not properly formatted

        var headers = Map.of("Content-Type", List.of("application/json"),
                "Content-Length", List.of(String.valueOf(body.length)));
        return new HttpResponse(200, headers, body);
    }

    private static void closeConnection(Socket connection) {
        try {
            System.out.println("Closing the connection...");
            connection.close();
        } catch (IOException e) {

        }
    }

    private static boolean shouldReuseConnection(Map<String, List<String>> headers) {
        return headers.getOrDefault(CONNECTION_HEADER, List.of(CONNECTION_HEADER_KEEP_ALIVE)).get(0).equals(CONNECTION_HEADER_KEEP_ALIVE);
    }

    private static void PrintRequest(HttpRequest request) {
        System.out.println("Method: " + request.method());
        System.out.println("Url: " + request.url());
        System.out.println("Headers:");
        request.headers().forEach((k, v) -> {
            System.out.println("%s - %s".formatted(k, v));
        });
        System.out.println("Body:");
        if(request.body().length > 0) {
            System.out.println(new String(request.body(), StandardCharsets.UTF_8));
        }
        else {
            System.out.println("Body is Empty");
        }
    }

    private static Map<String, List<String>> readHeaders(String[] lines) {
        var headers = new HashMap<String, List<String>>();
        for(int i = 1; i < lines.length; i++) {
            var line = lines[i];
            if(line.isEmpty()) break;
            var keyValue = line.split(":");
            var key = keyValue[0].toLowerCase().strip();
            var value = keyValue[1].strip();
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    private static byte[] readBody(InputStream stream, byte[] readBody, int expectedBodyLength) throws IOException {
        if(readBody.length == expectedBodyLength) return readBody;
        var result = new ByteArrayOutputStream(expectedBodyLength);
        result.write(readBody);
        var readBytes = readBody.length;
        var buffer = new byte[DEFAULT_BUFFER_SIZE];
        while(readBytes < expectedBodyLength) {
            var read = stream.read(buffer);
            if(read > 0) {
                result.write(buffer, 0, read);
                readBytes += read;
            }
            else {
                break;
            }
        }
        return result.toByteArray();
    }
}