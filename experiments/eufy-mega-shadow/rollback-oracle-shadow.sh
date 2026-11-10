#!/usr/bin/env bash
# Removes ONLY the isolated JH2 Mega SDK shadow, not production.
set -Eeuo pipefail
base="$HOME/.local/share/jh2-eufy-mega-shadow"
unit="$HOME/.config/systemd/user/jh2-eufy-mega-shadow.service"
if [[ ! -f "$base/current/.jh2-shadow-installed" ]]; then
 echo "REFUSED: shadow installer marker missing; production untouched" >&2
 exit 2
fi
if command -v systemctl >/dev/null; then
 systemctl --user disable --now jh2-eufy-mega-shadow.service >/dev/null 2>&1 || true
fi
if [[ -f "$base/shadow.pid" ]]; then
 pid="$(cat "$base/shadow.pid")"
 [[ "$pid" =~ ^[0-9]+$ ]] && kill "$pid" 2>/dev/null || true
fi
rm -f "$unit"
command -v systemctl >/dev/null && systemctl --user daemon-reload >/dev/null 2>&1 || true
rm -rf -- "$base"
echo "SHADOW_REMOVED: original JH2 code, data and services unchanged"
