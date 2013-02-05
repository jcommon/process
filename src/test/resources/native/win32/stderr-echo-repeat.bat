@echo off

set value=%~1
set repeat=%~2

for /L %%n in (1,1,%repeat%) do (
  echo %value% 1>&2
)