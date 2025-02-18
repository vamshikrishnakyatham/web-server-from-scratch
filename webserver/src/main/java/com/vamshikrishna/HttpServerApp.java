package com.vamshikrishna;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpServerApp {
    private static final String HTTP_NEW_LINE_DELIMITER = "\r\n";
    private static final String HTTP_HEAD_BODY_DELIMITER = HTTP_NEW_LINE_DELIMITER + HTTP_NEW_LINE_DELIMITER;
    private static final int DEFAULT_BUFFER_SIZE = 10_000;

    public static void main(String[] args) throws Exception {
        var serverSocket = new ServerSocket(8080);
        while(true) {
            var connection = serverSocket.accept();
            var request = readRequest(connection);
            if(request.isEmpty()) continue;
            PrintRequest(request.get());
            try(var os = connection.getOutputStream()) {
                var body = """
                        {
                            "id": 1
                        }
                        """;
                var response = """
                        HTTP/1.1 200 OK
                        Content-Type: application/json
                        Content-Length: %d
                        
                        %s
                        """.formatted(body.getBytes(StandardCharsets.UTF_8).length, body);
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static Optional<HttpReq> readRequest(Socket connection) throws Exception {
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
        var body = readBody(connection.getInputStream());
        return Optional.of(new HttpReq(method, url, headers, body));
    }

    private static byte[] readRawRequestHeader(InputStream stream) throws Exception {
        var toRead = stream.available();
        if(toRead == 0) toRead = DEFAULT_BUFFER_SIZE;
        var buffer = new byte[toRead];
        var read = stream.read(buffer);
        if(read <= 0) return new byte[0];
        return read == toRead ? buffer : Arrays.copyOf(buffer, read);
    }

    private static void PrintRequest(HttpReq request) {
        System.out.println("Method: " + request.method);
        System.out.println("Url: " + request.url);
        System.out.println("Headers:");
        request.headers.forEach((k, v) -> {
            System.out.println("%s - %s".formatted(k, v));
        });
    }

    private static Map<String, List<String>> readHeaders(String[] lines) {
        var headers = new HashMap<String, List<String>>();
        for(int i = 1; i < lines.length; i++) {
            var line = lines[i];
            if(line.isEmpty()) break;
            var keyValue = line.split(":");
            var key = keyValue[0].strip();
            var value = keyValue[1].strip();
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    private static byte[] readBody(InputStream stream) {
        return null;
    }

    private record HttpReq(String method, String url, Map<String, List<String>> headers, byte[] body) {

    }
}