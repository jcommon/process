@echo off

set value=%~1
set repeat=%~2

for /L %%n in (1,1,%repeat%) do (
  echo M:%%n %value% 1>&2
)