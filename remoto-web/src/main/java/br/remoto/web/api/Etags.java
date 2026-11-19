package br.remoto.web.api;

/** Converte o valor do cabeçalho If-Match ({@code "abc"} ou {@code W/"abc"}) no hash puro. */
final class Etags {

    private Etags() {
    }

    static String parse(String header) {
        if (header == null || header.isBlank() || header.trim().equals("*")) {
            return null;
        }
        String v = header.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
}
