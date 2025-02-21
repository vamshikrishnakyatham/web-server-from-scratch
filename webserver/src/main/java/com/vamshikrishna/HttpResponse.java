package com.vamshikrishna;

import java.util.List;
import java.util.Map;

record HttpResponse(int responseCode, Map<String, List<String>> headers, byte[] body) {

}
