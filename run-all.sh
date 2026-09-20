#!/usr/bin/env bash
# Runs all five MARSLINE tasks one by one and saves the real console output to ./evidence
set -u
mkdir -p evidence
for t in task1 task2 task3 task4 task5; do
  echo; echo "===== Running $t ====="
  mvn compile exec:java -Dexec.args="$t" 2>&1 | tee "evidence/$t.txt"
done
echo; echo 'Done. Console logs are saved in ./evidence'
