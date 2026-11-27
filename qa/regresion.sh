#!/usr/bin/env bash
# Regresión automática de Spur: corre todas las pruebas unitarias y, con --apk, compila el APK.
# Uso:  qa/regresion.sh          (solo pruebas, ~1 min)
#       qa/regresion.sh --apk    (pruebas + APK en app/build/outputs/apk/debug/app-debug.apk)
set -uo pipefail
cd "$(dirname "$0")/.."

echo "▶ Pruebas unitarias…"
./gradlew -q testDebugUnitTest > /tmp/spur-qa-gradle.log 2>&1
status=$?

dir=app/build/test-results/testDebugUnitTest
total=0; fail=0
for f in "$dir"/TEST-*.xml; do
  [ -e "$f" ] || continue
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | tr -dc 0-9)
  x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | tr -dc 0-9)
  e=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | tr -dc 0-9)
  total=$((total + t)); fail=$((fail + x + e))
  if [ $((x + e)) -gt 0 ]; then
    name=$(basename "$f" .xml | sed 's/TEST-co.eskala.bktpj_notification.//')
    echo "  ✗ $name: $((x + e)) fallan"
    awk 'match($0, /testcase name="[^"]*"/) { n = substr($0, RSTART + 15, RLENGTH - 16) }
         match($0, /<(failure|error) message="[^"]*"/) { m = substr($0, RSTART, RLENGTH); sub(/^<[a-z]+ message="/, "", m); sub(/"$/, "", m); gsub(/&lt;/, "<", m); gsub(/&gt;/, ">", m); gsub(/&quot;/, "\"", m); print "      - " n ": " m }' "$f" | head -10
  fi
done

if [ $status -ne 0 ] && [ $fail -eq 0 ]; then
  echo "✗ No compiló. Detalle:"; grep -E 'e: |error:' /tmp/spur-qa-gradle.log | head -20
  exit 1
fi
echo "Pruebas: $((total - fail)) de $total pasan."
echo "Reporte HTML: app/build/reports/tests/testDebugUnitTest/index.html"
[ $fail -gt 0 ] && exit 1

if [ "${1:-}" = "--apk" ]; then
  echo "▶ Compilando APK…"
  ./gradlew -q assembleDebug > /tmp/spur-qa-apk.log 2>&1 || { echo "✗ El APK no compiló"; grep -E 'e: ' /tmp/spur-qa-apk.log | head; exit 1; }
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
fi
echo "✓ Regresión automática OK. Falta la manual: qa/regresion-manual.md"
