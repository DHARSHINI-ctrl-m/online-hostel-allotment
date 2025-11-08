package com.hostel;

import java.io.File;
import java.sql.*;

public class Database {
    private static String dbPath;

    public static void init(String path) throws Exception {
        dbPath = path;
        File f = new File(dbPath).getParentFile();
        if (f != null && !f.exists()) f.mkdirs();
        try (Connection c = getConnection()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (id INTEGER PRIMARY KEY AUTOINCREMENT, room_number TEXT UNIQUE NOT NULL, capacity INTEGER NOT NULL, available INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS bookings (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL, room_id INTEGER NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, FOREIGN KEY(student_id) REFERENCES students(id), FOREIGN KEY(room_id) REFERENCES rooms(id))");
            }

            seed(c);
        }
    }

    private static void seed(Connection c) throws SQLException {
        // Admin user
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO students(name,email,password,role) VALUES(?,?,?,?)")) {
            ps.setString(1, "Administrator"); ps.setString(2, "admin@hostel.com"); ps.setString(3, "admin123"); ps.setString(4, "admin"); ps.executeUpdate();
        }
        // Student user
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO students(name,email,password,role) VALUES(?,?,?,?)")) {
            ps.setString(1, "Test Student"); ps.setString(2, "test@student.com"); ps.setString(3, "test123"); ps.setString(4, "student"); ps.executeUpdate();
        }
        // Rooms
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO rooms(id,room_number,capacity,available) VALUES(?,?,?,?)")) {
            insertRoom(ps, 1, "A101", 2, 2);
            insertRoom(ps, 2, "A102", 2, 2);
            insertRoom(ps, 3, "B201", 3, 3);
            insertRoom(ps, 4, "B202", 4, 4);
        }
    }

    private static void insertRoom(PreparedStatement ps, int id, String room, int capacity, int available) throws SQLException {
        ps.setInt(1, id); ps.setString(2, room); ps.setInt(3, capacity); ps.setInt(4, available); ps.executeUpdate();
    }

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(url);
    }
}







