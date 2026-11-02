#!/usr/bin/env bash
# End-to-end smoke test of the release APK on an emulator. Prints a PASS/FAIL report and small
# base64 screenshots to the log, and saves full screenshots in smoke/.
set -u
APK=app/build/outputs/apk/sideload/release/app-sideload-release.apk
PKG=com.gamedeals.watchlist
GAME_URL="https://psdeals.net/us-store/game/2997658/sonic-racing-crossworlds-ps4-ps5"
mkdir -p smoke
RESULTS=()
pass() { RESULTS+=("PASS  $1"); echo "::notice::PASS $1"; }
fail() { RESULTS+=("FAIL  $1"); echo "::error::FAIL $1"; }

dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > smoke/ui.xml 2>/dev/null; }
texts() { dump; tr '>' '\n' < smoke/ui.xml | grep -oE '(text|content-desc)="[^"]+"' | sed -E 's/^(text|content-desc)="//; s/"$//' | sort -u; }
has() { texts | grep -qF -- "$1"; }
wait_for() { # wait_for "<text>" <seconds>
  for _ in $(seq 1 "$2"); do has "$1" && return 0; sleep 1; done; return 1; }
tap() { # tap the first node whose text or content-desc equals $1
  dump
  local b; b=$(tr '>' '\n' < smoke/ui.xml | grep -m1 -E "(text|content-desc)=\"$1\"" | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && { echo "tap: '$1' not found"; return 1; }
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
shot() {
  adb exec-out screencap -p > "smoke/$1.png"
  convert "smoke/$1.png" -resize 320x -quality 55 "smoke/$1.jpg"
  echo "SHOT_BEGIN $1"; base64 -w0 "smoke/$1.jpg"; echo; echo "SHOT_END $1"
}

adb install -r "$APK" && pass "APK installs (release build, $(stat -c %s "$APK") bytes)" || fail "APK install"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

# 1. Launch -> Watchlist screen
adb shell am start -W -n $PKG/.MainActivity >/dev/null
if wait_for "Price Watchlist" 30; then pass "App starts on the Watchlist screen"; else fail "Watchlist screen not shown"; fi
has "Sync now" && pass "Site lists sync row shown" || fail "Sync row missing"
shot 1-watchlist

# 2. PlayStation tab loads psdeals.net live
tap "PlayStation"
if wait_for "psdeals.net" 45; then pass "PlayStation tab loads psdeals.net"; else fail "psdeals.net did not load"; fi
sleep 6
shot 2-psdeals

# 3. Open a real game page via deep link, tap Watch, check the detected price, save
adb shell am start -W -a android.intent.action.VIEW -d "$GAME_URL" -n $PKG/.MainActivity >/dev/null
sleep 3
if wait_for "Sonic Racing" 45; then pass "Game page opens from a psdeals link"; else fail "Game page did not open"; fi
sleep 5
tap "Watch" || true
if wait_for "Add to watchlist" 15; then pass "Watch dialog opens"; else fail "Watch dialog did not open"; fi
PRICE_LINE=$(texts | grep -m1 "Current price" || true)
echo "Detected: $PRICE_LINE"
if echo "$PRICE_LINE" | grep -qE '\$[0-9]'; then pass "Price detected from the live page ($PRICE_LINE)"; else fail "Price not detected ($PRICE_LINE)"; fi
shot 3-watch-dialog
tap "Save" || true
sleep 2

# 4. Watchlist shows the game with its price
tap "Watchlist"
if wait_for "Sonic Racing" 15; then pass "Game appears on the Watchlist"; else fail "Game missing from the Watchlist"; fi
texts | grep -E '^\$[0-9]' | head -3 | sed 's/^/  price shown: /'
shot 4-watchlist-with-game

# 5. Settings + background refresh
tap "Settings"
if wait_for "Background refresh" 10; then pass "Settings open (Background refresh section)"; else fail "Settings did not open"; fi
has "Daily (recommended)" && pass "Daily refresh option present" || fail "Daily option missing"
shot 5-settings
tap "Run background refresh now" || true
sleep 1
adb shell input keyevent KEYCODE_BACK
echo "Waiting for the background refresh to run…"
sleep 60
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >/dev/null
wait_for "Price Watchlist" 20
tap "Settings"
sleep 2
REFRESH=$(texts | grep -m1 -E "Last refresh|No background refresh" || true)
echo "Settings says: $REFRESH"
if echo "$REFRESH" | grep -q "Last refresh"; then pass "Background refresh ran ($REFRESH)"; else fail "Background refresh did not run ($REFRESH)"; fi
shot 6-after-refresh
adb shell input keyevent KEYCODE_BACK

# 6. Crashes / ANRs
adb logcat -d > smoke/logcat.txt
if grep -qE "FATAL EXCEPTION|ANR in $PKG" smoke/logcat.txt; then
  fail "Crash or ANR found in logcat"
  grep -A 25 -E "FATAL EXCEPTION|ANR in" smoke/logcat.txt | head -80
else
  pass "No crashes or ANRs in logcat"
fi

echo; echo "================ SMOKE TEST REPORT ================"
printf '%s\n' "${RESULTS[@]}"
echo "==================================================="
! printf '%s\n' "${RESULTS[@]}" | grep -q '^FAIL'
