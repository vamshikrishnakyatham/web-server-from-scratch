package com.vamshikrishna;

public interface HttpServer {
    void start(HttpRequestHandler handler);
    void stop();
}
