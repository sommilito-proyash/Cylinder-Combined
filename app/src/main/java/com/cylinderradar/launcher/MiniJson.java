package com.cylinderradar.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny, dependency-free JSON parser tuned for large GeoJSON/TopoJSON files.
 * Objects -> Map, arrays of only numbers -> double[] (memory-efficient), other arrays -> List,
 * numbers -> Double, strings -> String, true/false -> Boolean, null -> null.
 */
final class MiniJson {
    private final String s; private int p;
    private MiniJson(String s) { this.s = s; }

    static Object parse(String text) {
        MiniJson j = new MiniJson(text);
        j.ws();
        if (j.p < text.length() && text.charAt(j.p) == '﻿') { j.p++; j.ws(); }
        Object v = j.value();
        j.ws();
        if (j.p != text.length()) throw new IllegalArgumentException("JSON: extra data at " + j.p);
        return v;
    }

    private void ws() { while (p < s.length()) { char c = s.charAt(p); if (c == ' ' || c == '\n' || c == '\r' || c == '\t') p++; else break; } }
    private IllegalArgumentException err(String m) { return new IllegalArgumentException("JSON: " + m + " at " + p); }

    private Object value() {
        if (p >= s.length()) throw err("unexpected end");
        char c = s.charAt(p);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return Double.valueOf(number());
                throw err("unexpected '" + c + "'");
        }
    }
    private void expect(String w) { if (!s.startsWith(w, p)) throw err("expected " + w); p += w.length(); }

    private double number() {
        int st = p;
        while (p < s.length()) {
            char c = s.charAt(p);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') p++; else break;
        }
        if (st == p) throw err("bad number");
        return Double.parseDouble(s.substring(st, p));
    }

    private Map<String, Object> object() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        p++; ws();
        if (p < s.length() && s.charAt(p) == '}') { p++; return m; }
        while (true) {
            ws();
            if (p >= s.length() || s.charAt(p) != '"') throw err("expected key");
            String k = string(); ws();
            if (p >= s.length() || s.charAt(p) != ':') throw err("expected ':'");
            p++; ws();
            m.put(k, value()); ws();
            if (p >= s.length()) throw err("unterminated object");
            char c = s.charAt(p++);
            if (c == '}') return m;
            if (c != ',') throw err("expected ',' or '}'");
        }
    }

    private Object array() {
        p++; ws();
        if (p < s.length() && s.charAt(p) == ']') { p++; return new ArrayList<Object>(0); }
        char f = p < s.length() ? s.charAt(p) : 0;
        if (f == '-' || (f >= '0' && f <= '9')) {            // numeric array fast path
            double[] buf = new double[8]; int n = 0;
            while (true) {
                ws();
                if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                buf[n++] = number(); ws();
                if (p >= s.length()) throw err("unterminated array");
                char c = s.charAt(p++);
                if (c == ']') return n == buf.length ? buf : Arrays.copyOf(buf, n);
                if (c != ',') throw err("expected ',' or ']'");
            }
        }
        ArrayList<Object> l = new ArrayList<>();
        while (true) {
            ws(); l.add(value()); ws();
            if (p >= s.length()) throw err("unterminated array");
            char c = s.charAt(p++);
            if (c == ']') { l.trimToSize(); return l; }
            if (c != ',') throw err("expected ',' or ']'");
        }
    }

    private String string() {
        p++;
        StringBuilder sb = null; int st = p;
        while (true) {
            if (p >= s.length()) throw err("unterminated string");
            char c = s.charAt(p);
            if (c == '"') { String r = sb == null ? s.substring(st, p) : sb.append(s, st, p).toString(); p++; return r; }
            if (c == '\\') {
                if (sb == null) sb = new StringBuilder();
                sb.append(s, st, p); p++;
                char e = s.charAt(p++);
                switch (e) {
                    case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break; case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break; case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                    default: sb.append(e);
                }
                st = p;
            } else p++;
        }
    }

    // ---- helpers ----
    @SuppressWarnings("unchecked") static Map<String, Object> map(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }
    @SuppressWarnings("unchecked") static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : null; }
    static String str(Object o) { if (o == null) return null; if (o instanceof Double) { double d = (Double) o; return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); } return String.valueOf(o); }
}
