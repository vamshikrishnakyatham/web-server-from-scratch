package com.vamshikrishna;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;

public class HttpServerImpl implements HttpServer {
    private final Executor connectionHandler;
    private final int port;
    private final int connectionTimeout;
    private static final String HTTP_NEW_LINE_DELIMITER = "\r\n";
    private static final String HTTP_HEAD_BODY_DELIMITER = HTTP_NEW_LINE_DELIMITER + HTTP_NEW_LINE_DELIMITER;
    private static final int HTTP_HEAD_BODY_SEPARTOR_BYTES = HTTP_NEW_LINE_DELIMITER.getBytes(StandardCharsets.US_ASCII).length;
    private static final int DEFAULT_BUFFER_SIZE = 10_000;
    private static final String CONTENT_LENGTH_HEADER = "content-length";
    private static final String CONNECTION_HEADER = "connection";
    private static final String CONNECTION_HEADER_KEEP_ALIVE = "keep-alive";
    private ServerSocket serverSocket;
    private boolean isRunning;
    private HttpRequestHandler requestHandler;
    public HttpServerImpl(Executor connectionHandler, int port, int connectionTimeout) {
        this.connectionHandler = connectionHandler;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
    }
    @Override
    public void start(HttpRequestHandler requestHandler) {
        if(isServerRunning()) {
            throw new RuntimeException("Server is running on port %d already".formatted(port));
        }
        startServer(requestHandler);
    }
    private boolean isServerRunning() {
        return serverSocket != null && isRunning;
    }
    private void startServer(HttpRequestHandler requestHandler) {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to start the Http Server on port %s".formatted(port), e);
        }
        new Thread(() -> {
            try {
                while(isServerRunning()) {
                    var connection = serverSocket.accept();
                    connection.setSoTimeout(connectionTimeout);
                    connectionHandler.execute(() -> handleRequest(connection, requestHandler)); // to handle multiple client requests without blocking the server
                }
            }
            catch (Exception e) {
                if(isServerRunning()) {
                    stop();
                    throw new RuntimeException("Failed to accept the next connection...", e);
                }
                System.out.println("Closing the server...");
            }
        }).start();
    }

    private Optional<HttpRequest> readRequest(Socket connection) throws Exception {
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
    private int getExpectedBodyLength(Map<String, List<String>> headers) {
        try {
            return Integer.parseInt(headers.getOrDefault(CONTENT_LENGTH_HEADER, List.of("0")).get(0));
        } catch (Exception e) {
            return 0;
        }
    }
    private byte[] readRawRequestHeader(InputStream stream) throws Exception {
        var toRead = stream.available();
        if(toRead == 0) toRead = DEFAULT_BUFFER_SIZE;
        var buffer = new byte[toRead];
        var read = stream.read(buffer);
        if(read <= 0) return new byte[0];
        return read == toRead ? buffer : Arrays.copyOf(buffer, read);
    }
    private void handleRequest(Socket connection, HttpRequestHandler requestHandler) {
        try {
            var requestOpt = readRequest(connection);
            if (requestOpt.isEmpty()) {
                closeConnection(connection);
                return;
            }
            var request = requestOpt.get();
            PrintRequest(request);
            respondToRequest(connection, request, requestHandler);
            if (shouldReuseConnection(request.headers())) {
                // System.out.println("Reusing the connection...");
                handleRequest(connection, requestHandler);
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
    private void respondToRequest(Socket connection, HttpRequest req, HttpRequestHandler requestHandler) throws Exception {
        var res = requestHandler.handle(req);
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
    private void closeConnection(Socket connection) {
        try {
            System.out.println("Closing the connection...");
            connection.close();
        } catch (IOException e) {

        }
    }
    private boolean shouldReuseConnection(Map<String, List<String>> headers) {
        return headers.getOrDefault(CONNECTION_HEADER, List.of(CONNECTION_HEADER_KEEP_ALIVE)).get(0).equals(CONNECTION_HEADER_KEEP_ALIVE);
    }
    private void PrintRequest(HttpRequest request) {
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
    private Map<String, List<String>> readHeaders(String[] lines) {
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
    private byte[] readBody(InputStream stream, byte[] readBody, int expectedBodyLength) throws IOException {
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
    @Override
    public void stop() {
        if(isServerRunning()) {
            try {
                serverSocket.close();

            }
            catch (Exception e) {
                throw new RuntimeException("Fail to close the server", e);
            }
            finally {
                serverSocket = null;
                isRunning = false;
            }
        }
    }
}
