#!/usr/bin/env bash
# How hard PLVinyl works the phone over a minute: frames drawn per second, the app's
# CPU use (all its threads), and the phone's temperatures.
#
# Plug the phone in (USB debugging on), leave the app in the state to measure (playing
# by day, at night, stopped, the stats open...), then:
#
#   tools/battery_check.sh [seconds]          (default 60)
#
# Compare runs before and after a change, in the same state.
set -euo pipefail

PKG=io.github.plvzfqrit.plvinyl
SECS=${1:-60}

pid=$(adb shell pidof "$PKG" | tr -d '\r')
[ -n "$pid" ] || { echo "PLVinyl isn't running on the phone." >&2; exit 1; }
hz=$(adb shell getconf CLK_TCK 2>/dev/null | tr -d '\r' || true)
hz=${hz:-100}

cpu_ticks() { adb shell cat "/proc/$pid/stat" | awk '{ print $14 + $15 }'; }   # user + system

adb shell dumpsys gfxinfo "$PKG" reset > /dev/null
before=$(cpu_ticks)
sleep "$SECS"
after=$(cpu_ticks)
gfx=$(adb shell dumpsys gfxinfo "$PKG")

echo "$gfx" | awk -v s="$SECS" -v d="$((after - before))" -v hz="$hz" '
  /Total frames rendered/ && !f { f = $4 }
  /Janky frames:/ && !j { j = $3 " " $4 }
  END {
    printf "Frames drawn:  %.1f a second\n", f / s
    printf "Janky frames:  %s\n", j
    printf "App CPU:       %.1f%% of one core\n", 100 * d / hz / s
  }'

echo "Temperatures:"
adb shell dumpsys thermalservice | awk '
  /Thermal Status:/ && !st { st = 1; print "  thermal status: " $3 " (0 = none, 2 = moderate, 3 = severe)" }
  /Current temperatures from HAL/ { on = 1; next }
  on && /Temperature\{/ {
    match($0, /mName=[^,}]*/);  name = substr($0, RSTART + 6, RLENGTH - 6)
    match($0, /mValue=[^,}]*/); value = substr($0, RSTART + 7, RLENGTH - 7)
    if (toupper(name) ~ /SKIN|BAT|CPU|GPU|AP/ && n++ < 8) printf "  %s: %.1f °C\n", name, value
    next
  }
  on { on = 0 }'
adb shell dumpsys battery | awk '/temperature/ { printf "  battery (charger report): %.1f °C\n", $2 / 10; exit }'
