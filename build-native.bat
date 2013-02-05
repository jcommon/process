@echo off

REM **********************************************************************
REM * Builds native executables for testing purposes.
REM **********************************************************************

set DIR=%~dp0.
set CURR_DIR=%CD%
set BIN_DIR=%DIR%\src\test\resources\native\win32
set SRC_DIR=%DIR%\src\test\c
set OBJ_DIR=%DIR%\target\objs

cd /d "%DIR%"
goto setup

:setup
mkdir %BIN_DIR% 2>NUL
mkdir %OBJ_DIR% 2>NUL
goto build_all

:build_all
call :build stdin-1
call :build stderr-1
call :build stdout-1
echo Build complete.
goto done

:done
cd /d "%CURR_DIR%"
goto :EOF

:build %name%
setlocal ENABLEEXTENSIONS
set name=%~1

if not "x%OSSBUILD_DIR%" == "x" (
  if exist "%OSSBUILD_DIR%\sys\opt\mingw-w64\x86\bin" (
    call :build_full "%OSSBUILD_DIR%\sys\opt\mingw-w64\x86\bin\gcc.exe" "x86\bin\%name%.exe" %name%.o %name%.c
  )
  if exist "%OSSBUILD_DIR%\sys\opt\mingw-w64\x86_64\bin" (
    call :build_full "%OSSBUILD_DIR%\sys\opt\mingw-w64\x86_64\bin\gcc.exe" "x86_64\bin\%name%.exe" %name%.o %name%.c
  )
) else (
  if not "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    call :build_full "gcc" "x86\bin\%name%.exe" %name%.o %name%.c
  ) else (
    call :build_full "gcc" "x86_64\bin\%name%.exe" %name%.o %name%.c
  )
)
endlocal
goto :EOF

:build_full %gcc% %exe% %obj% %src%
setlocal ENABLEEXTENSIONS
set gcc=%~1
set exe=%~2
set obj=%~3
set src=%~4

REM get executable directory and ensure it exists
for %%i in ("%BIN_DIR%\%exe%") do set exedir=%%~dpi
mkdir "%exedir%" 2>NUL

echo Building %exe%...

"%gcc%" "-I%SRC_DIR%" -o "%OBJ_DIR%\%obj%" -c "%SRC_DIR%\%src%"
"%gcc%" -o "%BIN_DIR%\%exe%" "%OBJ_DIR%\%obj%"

endlocal
goto :EOF