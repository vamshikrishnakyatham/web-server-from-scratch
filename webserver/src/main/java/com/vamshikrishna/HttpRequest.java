package com.vamshikrishna;

import java.util.List;
import java.util.Map;

record HttpRequest(String method, String url, Map<String, List<String>> headers, byte[] body) {

}
