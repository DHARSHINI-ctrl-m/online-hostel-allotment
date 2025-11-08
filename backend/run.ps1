# PowerShell script to compile and run the Hostel Allotment backend
# Run this from the project root or from the backend directory

# Get the directory where this script is located
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = if (Test-Path "$scriptDir\src") { $scriptDir } else { "$scriptDir\backend" }

if (-not (Test-Path "$backendDir\src")) {
    Write-Host "Error: Cannot find backend directory. Please run from project root or backend directory." -ForegroundColor Red
    exit 1
}

# Change to backend directory so relative paths work correctly
Push-Location $backendDir

# Set classpath
$env:CP = "src;lib\*"

Write-Host "Compiling Java files..." -ForegroundColor Yellow

# Get all Java files and compile them
$javaFiles = Get-ChildItem -Path "src\com\hostel\*.java" -Recurse
if ($javaFiles.Count -eq 0) {
    Write-Host "Error: No Java files found in src\com\hostel\" -ForegroundColor Red
    Pop-Location
    exit 1
}

# Compile
javac -cp $env:CP $javaFiles.FullName

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    Pop-Location
    exit 1
}

Write-Host "Compilation successful!" -ForegroundColor Green
Write-Host "Starting server..." -ForegroundColor Yellow
Write-Host ""

# Run the application
java -cp $env:CP com.hostel.Main

# Restore original directory
Pop-Location

