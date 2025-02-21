package com.vamshikrishna;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpServerApp {
    public static void main(String[] args) throws Exception{
        var server = new HttpServerImpl(Executors.newFixedThreadPool(10), 8080, 10_000);
        server.start(r -> {
            var body = """
                        {
                            "id": 1,
                            "request_url": "%s"
                        }
                        """.formatted(r.url()).getBytes(StandardCharsets.UTF_8);

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
        });
        System.out.println("Server has started...");
//        Thread.sleep(1000);
//        System.out.println("Stopping the server...");
//        server.stop();
    }
}
