@echo off
setlocal
cd /d "%~dp0"

where python >nul 2>&1
if %ERRORLEVEL%==0 (
  python server.py
  goto :eof
)

where py >nul 2>&1
if %ERRORLEVEL%==0 (
  py server.py
  goto :eof
)

echo Python 3.10+ is required.
echo Install from https://www.python.org/downloads/ and re-run start.bat
echo or:  python server.py
echo or:  py server.py
pause
