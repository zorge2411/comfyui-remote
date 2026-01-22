@echo off
setlocal enabledelayedexpansion

:: 1. Clone the GSD template
echo Cloning repository...
git clone https://github.com/toonight/get-shit-done-for-antigravity.git gsd-template
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Failed to clone the repository. Check your internet connection or git installation.
    goto :error
)

:: 2. Copy the hidden directories
echo Copying files...
:: Vi kører xcopy for hver mappe og tjekker for fejl
set "folders=.agent .gemini .gsd"

for %%f in (%folders%) do (
    if exist "gsd-template\%%f" (
        xcopy "gsd-template\%%f" "%%f\" /E /I /H /Y >nul
        if !ERRORLEVEL! neq 0 (
            echo [ERROR] Failed to copy folder: %%f
            goto :error
        )
    ) else (
        echo [WARNING] Folder %%f not found in template, skipping...
    )
)

:: 3. Clean up
echo Cleaning up template folder...
rd /s /q gsd-template
if %ERRORLEVEL% neq 0 (
    echo [WARNING] Could not delete gsd-template folder. Please delete it manually.
)

echo.
echo ==========================================
echo GSD template setup completed successfully!
echo ==========================================
pause
exit /b 0

:error
echo.
echo ==========================================
echo [FATAL] Script stopped due to an error.
echo ==========================================
pause
exit /b 1