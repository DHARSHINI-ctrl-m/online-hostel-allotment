@echo off
REM Batch file to compile and run the Hostel Allotment backend (for CMD)
REM Run this from the project root or from the backend directory

cd /d "%~dp0"
if not exist "src\com\hostel" (
    if exist "..\backend\src\com\hostel" (
        cd /d "%~dp0\.."
        cd backend
    ) else (
        echo Error: Cannot find backend directory.
        pause
        exit /b 1
    )
)

set CP=src;lib\*
echo Compiling Java files...
javac -cp %CP% src\com\hostel\*.java

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!
echo Starting server...
echo.

java -cp %CP% com.hostel.Main

pause






