@echo off
REM Runs all five MARSLINE tasks one by one and saves the real console output to the "evidence" folder.
REM Take your screenshots from these runs (or from a normal cmd window running the same commands).
if not exist evidence mkdir evidence
for %%T in (task1 task2 task3 task4 task5) do (
  echo.
  echo ===== Running %%T =====
  call mvn compile exec:java "-Dexec.args=%%T" > evidence\%%T.txt 2>&1
  type evidence\%%T.txt
)
echo.
echo Done. Console logs are saved in the "evidence" folder.
