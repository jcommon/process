@echo off

set repeat=%~1
set value=%~2

for /L %%n in (1,1,%repeat%) do (call :every_other %%n)
goto end

:every_other
set n=%1
set /a modulo = "%n% %% 2"
if %modulo% == 0 (
  call :even %n%
) else (
  call :odd %n%
)
goto :eof

:odd
set n=%1
echo M:%n% %value%
goto :eof

:even
set n=%1
echo M:%n% %value% 1>&2
goto :eof

:end
