@echo off

setlocal enabledelayedexpansion

for %%v in (%*) do (
  set VAR_NAME=%%v
  set VAR_VALUE=!%%v!

  echo !VAR_NAME!=!VAR_VALUE!
)
