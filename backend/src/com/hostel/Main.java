package com.hostel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class Main {
    private static final int PORT = 8080;
    private static final String DB_PATH = "data/hostel.db";
    private static final Map<String, Session> tokenToSession = new HashMap<>();

    public static void main(String[] args) throws Exception {
        Database.init(DB_PATH);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/register", Main::handleRegister);
        server.createContext("/api/login", Main::handleLogin);
        server.createContext("/api/logout", Main::handleLogout);
        server.createContext("/api/rooms", Main::handleRooms);
        server.createContext("/api/book", Main::handleBook);
        server.createContext("/api/myBooking", Main::handleMyBooking);
        server.createContext("/api/admin/rooms", Main::handleAdminRooms);
        server.createContext("/api/admin/bookings", Main::handleAdminBookings);

        server.setExecutor(null);
        System.out.println("Hostel server running on http://localhost:" + PORT);
        server.start();
    }

    // ===== Handlers =====
    private static void handleRegister(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, jsonMsg("Only POST")); return; }
        Map<String, Object> body = readJson(ex);
        String name = asString(body.get("name"));
        String email = asString(body.get("email"));
        String password = asString(body.get("password"));
        if (isEmpty(name) || isEmpty(email) || isEmpty(password)) { sendJson(ex, 400, jsonMsg("Missing fields")); return; }
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO students(name,email,password,role) VALUES(?,?,?,?)")) {
                ps.setString(1, name); ps.setString(2, email); ps.setString(3, password); ps.setString(4, "student");
                ps.executeUpdate();
            }
            sendJson(ex, 200, mapOf("success", true));
        } catch (SQLException e) {
            sendJson(ex, 400, mapOf("success", false, "message", "Email already exists"));
        }
    }

    private static void handleLogin(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, jsonMsg("Only POST")); return; }
        Map<String, Object> body = readJson(ex);
        String email = asString(body.get("email"));
        String password = asString(body.get("password"));
        String role = Optional.ofNullable(asString(body.get("role"))).orElse("student");
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id,name,email,role FROM students WHERE email=? AND password=? AND role=?")) {
                ps.setString(1, email); ps.setString(2, password); ps.setString(3, role);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("name", rs.getString("name"));
                    user.put("email", rs.getString("email"));
                    user.put("role", rs.getString("role"));
                    String token = UUID.randomUUID().toString();
                    tokenToSession.put(token, new Session(rs.getInt("id"), rs.getString("role")));
                    sendJson(ex, 200, mapOf("success", true, "token", token, "user", user));
                } else {
                    sendJson(ex, 401, mapOf("success", false, "message", "Invalid credentials"));
                }
            }
        } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
    }

    private static void handleLogout(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
        if (token != null) tokenToSession.remove(token);
        sendJson(ex, 200, mapOf("success", true));
    }

    private static void handleRooms(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, jsonMsg("Only GET")); return; }
        try (Connection c = Database.getConnection()) {
            List<Map<String, Object>> rooms = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id,room_number,capacity,available FROM rooms ORDER BY room_number")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rooms.add(mapOf(
                            "id", rs.getInt("id"),
                            "roomNumber", rs.getString("room_number"),
                            "capacity", rs.getInt("capacity"),
                            "available", rs.getInt("available")
                    ));
                }
            }
            sendJson(ex, 200, mapOf("rooms", rooms));
        } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
    }

    private static void handleBook(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, jsonMsg("Only POST")); return; }
        Session session = requireAuth(ex, "student"); if (session == null) return;
        Map<String, Object> body = readJson(ex);
        Integer roomId = asInt(body.get("roomId"));
        if (roomId == null) { sendJson(ex, 400, jsonMsg("roomId required")); return; }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Ensure student has no active booking
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM bookings WHERE student_id=? AND status='active'")) {
                    ps.setInt(1, session.userId);
                    if (ps.executeQuery().next()) { c.rollback(); sendJson(ex, 400, jsonMsg("You already have an active booking")); return; }
                }
                // Check room availability
                int available = 0; String roomNumber = null;
                try (PreparedStatement ps = c.prepareStatement("SELECT room_number,available FROM rooms WHERE id=?")) {
                    ps.setInt(1, roomId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) { available = rs.getInt("available"); roomNumber = rs.getString("room_number"); }
                }
                if (available <= 0) { c.rollback(); sendJson(ex, 400, jsonMsg("Room not available")); return; }
                // Create booking
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO bookings(student_id,room_id,status,created_at) VALUES(?,?,?,?)")) {
                    ps.setInt(1, session.userId); ps.setInt(2, roomId); ps.setString(3, "active"); ps.setString(4, LocalDateTime.now().toString());
                    ps.executeUpdate();
                }
                // Decrement availability
                try (PreparedStatement ps = c.prepareStatement("UPDATE rooms SET available=available-1 WHERE id=?")) {
                    ps.setInt(1, roomId); ps.executeUpdate();
                }
                c.commit();
                sendJson(ex, 200, mapOf("success", true, "roomNumber", roomNumber));
            } catch (SQLException err) { c.rollback(); throw err; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
    }

    private static void handleMyBooking(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        Session session = requireAuth(ex, "student"); if (session == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            try (Connection c = Database.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement("SELECT b.id,b.status,b.created_at,r.room_number FROM bookings b JOIN rooms r ON b.room_id=r.id WHERE b.student_id=? AND b.status='active'")) {
                    ps.setInt(1, session.userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        Map<String, Object> bk = mapOf(
                                "id", rs.getInt("id"),
                                "status", rs.getString("status"),
                                "createdAt", rs.getString("created_at"),
                                "roomNumber", rs.getString("room_number")
                        );
                        sendJson(ex, 200, mapOf("booking", bk));
                    } else { sendJson(ex, 200, mapOf("booking", null)); }
                }
            } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
        } else if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            try (Connection c = Database.getConnection()) {
                c.setAutoCommit(false);
                try {
                    Integer roomId = null;
                    Integer bookingId = null;
                    try (PreparedStatement ps = c.prepareStatement("SELECT id,room_id FROM bookings WHERE student_id=? AND status='active'")) {
                        ps.setInt(1, session.userId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) { bookingId = rs.getInt("id"); roomId = rs.getInt("room_id"); }
                    }
                    if (bookingId == null) { c.rollback(); sendJson(ex, 400, jsonMsg("No active booking")); return; }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status='cancelled' WHERE id=?")) {
                        ps.setInt(1, bookingId); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE rooms SET available=available+1 WHERE id=?")) {
                        ps.setInt(1, roomId); ps.executeUpdate();
                    }
                    c.commit();
                    sendJson(ex, 200, mapOf("success", true));
                } catch (SQLException err) { c.rollback(); throw err; }
                finally { c.setAutoCommit(true); }
            } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
        } else { sendJson(ex, 405, jsonMsg("Method not allowed")); }
    }

    private static void handleAdminRooms(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        Session session = requireAuth(ex, "admin"); if (session == null) return;
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> body = readJson(ex);
            String roomNumber = asString(body.get("roomNumber"));
            Integer capacity = asInt(body.get("capacity"));
            Integer available = asInt(body.get("available"));
            if (isEmpty(roomNumber) || capacity == null || available == null) { sendJson(ex, 400, jsonMsg("Missing fields")); return; }
            try (Connection c = Database.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO rooms(room_number,capacity,available) VALUES(?,?,?)")) {
                    ps.setString(1, roomNumber); ps.setInt(2, capacity); ps.setInt(3, available);
                    ps.executeUpdate();
                }
                sendJson(ex, 200, mapOf("success", true));
            } catch (SQLException e) { sendJson(ex, 400, mapOf("success", false, "message", "Room already exists")); }
        } else { sendJson(ex, 405, jsonMsg("Only POST")); }
    }

    private static void handleAdminBookings(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { emptyOk(ex); return; }
        Session session = requireAuth(ex, "admin"); if (session == null) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, jsonMsg("Only GET")); return; }
        try (Connection c = Database.getConnection()) {
            List<Map<String, Object>> bookings = new ArrayList<>();
            String sql = "SELECT b.id,b.status,b.created_at,s.name,s.email,r.room_number FROM bookings b JOIN students s ON b.student_id=s.id JOIN rooms r ON b.room_id=r.id ORDER BY b.id DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    bookings.add(mapOf(
                            "id", rs.getInt("id"),
                            "status", rs.getString("status"),
                            "createdAt", rs.getString("created_at"),
                            "studentName", rs.getString("name"),
                            "studentEmail", rs.getString("email"),
                            "roomNumber", rs.getString("room_number")
                    ));
                }
            }
            sendJson(ex, 200, mapOf("bookings", bookings));
        } catch (SQLException e) { sendJson(ex, 500, jsonMsg("DB error")); }
    }

    // ===== Helpers =====
    private static void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,X-Auth-Token");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
    }
    private static void emptyOk(HttpExchange ex) throws IOException { ex.sendResponseHeaders(204, -1); }

    private static Session requireAuth(HttpExchange ex, String role) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
        Session session = tokenToSession.get(token);
        if (session == null || (role != null && !role.equals(session.role))) {
            sendJson(ex, 401, jsonMsg("Unauthorized"));
            return null;
        }
        return session;
    }

    private static Map<String, Object> jsonMsg(String m) { return mapOf("message", m); }

    private static Map<String, Object> readJson(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return new HashMap<>();
            return Json.parse(text);
        }
    }

    private static void sendJson(HttpExchange ex, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static boolean isEmpty(String s) { return s == null || s.isBlank(); }
    private static String asString(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer asInt(Object o) { try { return o == null ? null : Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; } }

    // Helper method to create maps easily
    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }

    private record Session(int userId, String role) {}
}


