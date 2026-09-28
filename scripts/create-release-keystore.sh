#!/usr/bin/env bash
# Creates the DoseTrack release signing key with OpenSSL only (no Java/keytool needed).
#
# Usage: scripts/create-release-keystore.sh [output-dir]   (default: ~/DoseTrack-signing)
#
# Output (keep OUTSIDE the repository, back it up; losing it means you can never update the app):
#   dosetrack-release.p12            PKCS12 keystore (alias "dosetrack")
#   RELEASE_KEYSTORE_BASE64.txt      -> GitHub secret RELEASE_KEYSTORE_BASE64
#   RELEASE_KEYSTORE_PASSWORD.txt    -> GitHub secret RELEASE_KEYSTORE_PASSWORD
#   RELEASE_KEY_ALIAS.txt            -> GitHub secret RELEASE_KEY_ALIAS
# The password is random and never printed.
set -euo pipefail

# Git Bash on Windows rewrites arguments that look like paths (e.g. "/CN=..."); turn that off and
# convert real paths explicitly so the native openssl.exe receives Windows paths.
export MSYS_NO_PATHCONV=1
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

OUT_DIR="$(native_path "${1:-$HOME/DoseTrack-signing}")"
ALIAS="dosetrack"
KEYSTORE="$OUT_DIR/dosetrack-release.p12"

if [ -e "$KEYSTORE" ]; then
  echo "Refusing to overwrite existing keystore: $KEYSTORE" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
umask 077

KEYSTORE_PASSWORD="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-32)"
export KEYSTORE_PASSWORD

TMP_DIR="$(native_path "$(mktemp -d)")"
trap 'rm -rf "$TMP_DIR"' EXIT

# Self-signed certificate valid ~27 years (Play requires validity past 2033).
openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -nodes \
  -keyout "$TMP_DIR/key.pem" -out "$TMP_DIR/cert.pem" \
  -subj "/CN=DoseTrack/O=DoseTrack" 2>/dev/null

openssl pkcs12 -export \
  -inkey "$TMP_DIR/key.pem" -in "$TMP_DIR/cert.pem" \
  -name "$ALIAS" -out "$KEYSTORE" \
  -passout env:KEYSTORE_PASSWORD

base64 -w0 "$KEYSTORE" > "$OUT_DIR/RELEASE_KEYSTORE_BASE64.txt"
printf '%s' "$KEYSTORE_PASSWORD" > "$OUT_DIR/RELEASE_KEYSTORE_PASSWORD.txt"
printf '%s' "$ALIAS" > "$OUT_DIR/RELEASE_KEY_ALIAS.txt"

echo "Keystore created in: $OUT_DIR"
echo "Certificate SHA-256 fingerprint (safe to share):"
openssl x509 -in "$TMP_DIR/cert.pem" -noout -fingerprint -sha256
