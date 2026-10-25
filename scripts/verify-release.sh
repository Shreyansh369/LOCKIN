#!/usr/bin/env bash
# Verifies a LOCKIN release artifact (APK or AAB) is offline-only and backup-free.
#
# FAILS if the merged manifest contains:
#   - android.permission.INTERNET
#   - android.permission.ACCESS_NETWORK_STATE
#   - android.permission.QUERY_ALL_PACKAGES
#   - android:allowBackup="true" (or allowBackup missing, which defaults to true)
# Also prints every permission, the variant meta-data and backup-related flags.
#
# Usage:
#   scripts/verify-release.sh path/to/app.apk
#   scripts/verify-release.sh path/to/app.aab
#
# Tools (first one found is used):
#   APK: apkanalyzer ($ANDROID_HOME/cmdline-tools/latest/bin) or aapt2 ($ANDROID_HOME/build-tools/<v>/)
#   AAB: bundletool (on PATH, or BUNDLETOOL_JAR=/path/to/bundletool.jar)
# https://developer.android.com/tools/apkanalyzer
# https://developer.android.com/tools/aapt2
# https://developer.android.com/tools/bundletool
set -euo pipefail

die() { echo "verify-release: $*" >&2; exit 2; }

[[ $# -eq 1 ]] || die "usage: $0 <app.apk|app.aab>"
ARTIFACT="$1"
[[ -f "$ARTIFACT" ]] || die "no such file: $ARTIFACT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

find_tool() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return; fi
  [[ -n "$SDK" ]] || return 1
  local hit
  case "$name" in
    apkanalyzer) hit=$(ls "$SDK"/cmdline-tools/*/bin/apkanalyzer 2>/dev/null | sort -V | tail -1) ;;
    aapt2) hit=$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1) ;;
  esac
  [[ -n "${hit:-}" ]] && echo "$hit"
}

# Prints the merged manifest. Format "xml" (apkanalyzer/bundletool) or "aapt2" (xmltree dump).
FORMAT=xml
case "$ARTIFACT" in
  *.apk)
    if TOOL=$(find_tool apkanalyzer); then
      MANIFEST=$("$TOOL" manifest print "$ARTIFACT")
    elif TOOL=$(find_tool aapt2); then
      FORMAT=aapt2
      MANIFEST=$("$TOOL" dump xmltree --file AndroidManifest.xml "$ARTIFACT")
    else
      die "need apkanalyzer or aapt2 (set ANDROID_HOME)"
    fi
    ;;
  *.aab)
    if command -v bundletool >/dev/null 2>&1; then
      MANIFEST=$(bundletool dump manifest --bundle "$ARTIFACT")
    elif [[ -n "${BUNDLETOOL_JAR:-}" ]]; then
      MANIFEST=$(java -jar "$BUNDLETOOL_JAR" dump manifest --bundle "$ARTIFACT")
    else
      die "need bundletool on PATH or BUNDLETOOL_JAR for .aab"
    fi
    ;;
  *) die "expected .apk or .aab" ;;
esac

[[ -n "$MANIFEST" ]] || die "empty manifest dump"

# --- extract -----------------------------------------------------------------
if [[ "$FORMAT" == "xml" ]]; then
  FLAT=$(tr '\n' ' ' <<<"$MANIFEST")
  PERMS=$(grep -oE '<uses-permission(-sdk-23)?[^>]*android:name="[^"]+"' <<<"$FLAT" \
    | grep -oE 'android:name="[^"]+"' | sed -E 's/android:name="([^"]+)"/\1/' | sort -u || true)
  APP_TAG=$(grep -oE '<application[^>]*>' <<<"$FLAT" | head -1 || true)
  attr() { grep -oE "android:$1=\"[^\"]*\"" <<<"$APP_TAG" | head -1 | sed -E 's/.*="([^"]*)"/\1/' || true; }
  ALLOW_BACKUP=$(attr allowBackup)
  DATA_RULES=$(attr dataExtractionRules)
  VARIANT=$(grep -oE '<meta-data[^>]*android:name="lockin.variant"[^>]*>' <<<"$FLAT" \
    | grep -oE 'android:value="[^"]*"' | sed -E 's/.*="([^"]*)"/\1/' || true)
else
  # aapt2 xmltree: element lines "E: tag (line=N)", attribute lines
  # "A: http://schemas.android.com/apk/res/android:name(0x01010003)="value" (Raw: "value")".
  PERMS=$(awk '/E: uses-permission/{p=1;next} /E: /{p=0} p && /android:name\(/' <<<"$MANIFEST" \
    | grep -oE '="[^"]+"' | sed -E 's/="([^"]+)"/\1/' | sort -u || true)
  APP_BLOCK=$(awk '/E: application/{p=1;print;next} p && /E: /{exit} p' <<<"$MANIFEST")
  ALLOW_BACKUP_RAW=$(grep -E 'android:allowBackup\(' <<<"$APP_BLOCK" | head -1 || true)
  if [[ -z "$ALLOW_BACKUP_RAW" ]]; then ALLOW_BACKUP=""
  elif grep -qE '=(false|\(type 0x12\)0x0\b)' <<<"$ALLOW_BACKUP_RAW"; then ALLOW_BACKUP=false
  else ALLOW_BACKUP=true; fi
  DATA_RULES=$(grep -E 'android:dataExtractionRules\(' <<<"$APP_BLOCK" | grep -oE '@[^ ]+' | head -1 || true)
  VARIANT=$(grep -A2 -E 'android:name\(0x01010003\)="lockin.variant"' <<<"$MANIFEST" \
    | grep -oE 'android:value\([^)]*\)="[^"]*"' | sed -E 's/.*="([^"]*)"/\1/' | head -1 || true)
fi

# --- report ------------------------------------------------------------------
echo "Artifact:            $ARTIFACT"
echo "lockin.variant:      ${VARIANT:-<missing>}"
echo "allowBackup:         ${ALLOW_BACKUP:-<missing → defaults to true>}"
echo "dataExtractionRules: ${DATA_RULES:-<missing>}"
echo "Permissions:"
if [[ -n "$PERMS" ]]; then sed 's/^/  - /' <<<"$PERMS"; else echo "  (none)"; fi

FAIL=0
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE android.permission.QUERY_ALL_PACKAGES; do
  if grep -qxF "$forbidden" <<<"$PERMS"; then
    echo "FAIL: $forbidden is present" >&2
    FAIL=1
  fi
done
if [[ "$ALLOW_BACKUP" != "false" ]]; then
  echo "FAIL: android:allowBackup is not false" >&2
  FAIL=1
fi
if [[ -z "$DATA_RULES" ]]; then
  echo "WARN: android:dataExtractionRules missing (Android 12+ device-to-device transfer not excluded)" >&2
fi
[[ -n "$VARIANT" ]] || echo "WARN: lockin.variant meta-data missing" >&2

if [[ $FAIL -ne 0 ]]; then
  echo "RESULT: FAIL" >&2
  exit 1
fi
echo "RESULT: PASS"
