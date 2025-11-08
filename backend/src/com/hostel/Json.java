package com.hostel;

import java.util.*;

public class Json {
    // Minimal JSON parser/stringifier for simple objects/arrays with strings/numbers/booleans/null
    public static Map<String, Object> parse(String json) {
        return new Parser(json).parseObject();
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof String) sb.append('"').append(escape((String) v)).append('"');
        else if (v instanceof Number || v instanceof Boolean) sb.append(String.valueOf(v));
        else if (v instanceof Map) {
            sb.append('{'); boolean first = true;
            for (Object e : ((Map<?, ?>) v).entrySet()) {
                Map.Entry<?, ?> me = (Map.Entry<?, ?>) e;
                if (!first) sb.append(','); first = false;
                sb.append('"').append(escape(String.valueOf(me.getKey()))).append('"').append(':');
                writeValue(sb, me.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('['); boolean first = true;
            for (Object it : (Iterable<?>) v) { if (!first) sb.append(','); first = false; writeValue(sb, it); }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }

    private static class Parser {
        private final String s; int i = 0;
        Parser(String s) { this.s = s.trim(); }
        Map<String, Object> parseObject() {
            skipWs(); expect('{'); Map<String, Object> m = new LinkedHashMap<>(); skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs(); String key = parseString(); skipWs(); expect(':'); skipWs(); Object val = parseValue(); m.put(key, val); skipWs();
                char c = peek(); if (c == ',') { i++; continue; } if (c == '}') { i++; break; } throw err("Expected , or }");
            }
            return m;
        }
        List<Object> parseArray() {
            skipWs(); expect('['); List<Object> a = new ArrayList<>(); skipWs();
            if (peek() == ']') { i++; return a; }
            while (true) {
                skipWs(); a.add(parseValue()); skipWs();
                char c = peek(); if (c == ',') { i++; continue; } if (c == ']') { i++; break; } throw err("Expected , or ]");
            }
            return a;
        }
        Object parseValue() {
            skipWs(); char c = peek();
            if (c == '"') return parseString();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (startsWith("true")) { i += 4; return Boolean.TRUE; }
            if (startsWith("false")) { i += 5; return Boolean.FALSE; }
            if (startsWith("null")) { i += 4; return null; }
            return parseNumber();
        }
        String parseString() {
            expect('"'); StringBuilder sb = new StringBuilder();
            while (i < s.length()) { char c = s.charAt(i++); if (c == '"') break; if (c == '\\') { char n = s.charAt(i++); if (n == 'n') sb.append('\n'); else sb.append(n); } else sb.append(c); }
            return sb.toString();
        }
        Number parseNumber() {
            int start = i; while (i < s.length()) { char c = s.charAt(i); if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++; else break; }
            String num = s.substring(start, i);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }
        void skipWs() { while (i < s.length()) { char c = s.charAt(i); if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++; else break; } }
        void expect(char c) { if (peek() != c) throw err("Expected '" + c + "'"); i++; }
        boolean startsWith(String t) { return s.startsWith(t, i); }
        char peek() { if (i >= s.length()) throw err("Unexpected end"); return s.charAt(i); }
        RuntimeException err(String m) { return new RuntimeException(m + " at pos " + i); }
    }
}







