# Hostel Allotment System (Java + SQLite)

A simple online hostel allotment system with a separate frontend and backend.

- Frontend: HTML/CSS/JS (Bootstrap)
- Backend: Pure Java (embedded `HttpServer`), REST endpoints
- Database: SQLite (via JDBC)

No Maven/Gradle. Works by compiling Java files and running a single class. SQLite requires only a single JDBC jar file.

## Project Structure

```
frontend/
  index.html
  dashboard.html
  rooms.html
  book.html
  my-booking.html
  admin.html
  assets/
    style.css
    app.js
backend/
  src/com/hostel/
    Main.java
    Database.java
    Json.java
  data/            # SQLite database file will be created here (hostel.db)
  lib/             # Put sqlite-jdbc jar here
```

## Prerequisites

- Java 17+ (JDK) installed and on PATH
- Download the SQLite JDBC driver jar and place it in `backend/lib/`
  - Get `sqlite-jdbc-<version>.jar` from `https://github.com/xerial/sqlite-jdbc/reHleases`

Example used during testing: `sqlite-jdbc-3.45.2.0.jar`

## Running the Backend

### Option 1: Using the Run Scripts (Recommended)

**PowerShell:**
```powershell
cd backend
.\run.ps1
```

**CMD (Command Prompt):**
```cmd
cd backend
run.bat
```

### Option 2: Manual Commands

**PowerShell:**
```powershell
cd backend
$env:CP="src;lib\*"
$javaFiles = Get-ChildItem -Path "src\com\hostel\*.java" -Recurse
javac -cp $env:CP $javaFiles.FullName
java -cp $env:CP com.hostel.Main
```

**CMD (Command Prompt):**
```cmd
cd backend
set CP=src;lib\*
javac -cp %CP% src\com\hostel\*.java
java -cp %CP% com.hostel.Main
```

You should see: "Hostel server running on http://localhost:8080"

The server auto-creates the SQLite database at `backend/data/hostel.db` and seeds:
- Admin: email `admin@hostel.com` / password `admin123`
- Student: email `test@student.com` / password `test123`
- Sample rooms: A101, A102, B201, B202

## Running the Frontend

Just open the HTML files directly in your browser (no server needed):
- `frontend/index.html` → login/register page

After login as student you can navigate to:
- `dashboard.html`, `rooms.html`, `book.html`, `my-booking.html`

Admin goes to:
- `admin.html`

Note: The frontend calls the backend at `http://localhost:8080/api`. Ensure the backend is running first.

## Features

- Student registration and login (session via token stored in localStorage)
- List available rooms
- Book a room (availability decrements automatically)
- View/cancel own booking
- Admin: add rooms, view all bookings

## Endpoints (brief)

- POST `/api/register` { name, email, password }
- POST `/api/login` { email, password, role: "student"|"admin" }
- POST `/api/logout`
- GET  `/api/rooms`
- POST `/api/book` { roomId } [student]
- GET  `/api/myBooking` [student]
- DELETE `/api/myBooking` [student]
- POST `/api/admin/rooms` { roomNumber, capacity, available } [admin]
- GET  `/api/admin/bookings` [admin]

Auth via header `X-Auth-Token` returned on login.

## Import into VS Code or Eclipse

- VS Code: Open folder → compile with the PowerShell commands above. You can also set a simple build task.
- Eclipse: Create a Java project pointing to `backend`, add `backend/src` to source, and add `backend/lib/sqlite-jdbc-*.jar` to Build Path. Run `com.hostel.Main`.

## Notes

- Passwords are stored in plaintext for simplicity. For production, hash passwords (e.g., BCrypt).
- Sessions are in-memory; restarting the server logs users out.
- CORS is enabled for local files to access the API.

Enjoy!


