@echo off

set repeat=%~1
set value=%~2

for /L %%n in (1,1,%repeat%) do (
  echo M:%%n %value%
)

REM PING 127.0.0.1 -n 6
