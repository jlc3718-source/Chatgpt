#!/usr/bin/env bash
# Make the already-installed, read-only JH2 Mega SDK shadow survive logout and reboot.
# No Eufy login, Eufy commands, production app, firmware or database changes.
set -Eeuo pipefail
umask 077
unit=jh2-eufy-mega-shadow.service
base="$HOME/.local/share/jh2-eufy-mega-shadow"
user="$(id -un)"
service="$HOME/.config/systemd/user/$unit"
fail(){ printf 'PERMANENCE_FAILED: %s\n' "$*" >&2; exit 1; }
[[ -f "$base/current/.jh2-shadow-installed" ]] || fail "SDK shadow installation missing"
[[ -f "$service" ]] || fail "SDK user service unit missing"
[[ -x "$base/current/node/bin/node" ]] || fail "SDK Node 24 executable missing"
[[ -f "$base/current/.env" ]] || fail "SDK configuration missing"
command -v loginctl >/dev/null || fail "loginctl unavailable"
command -v systemctl >/dev/null || fail "systemctl unavailable"
command -v sudo >/dev/null || fail "sudo unavailable"
[[ "$user" != root ]] || fail "Use the original Ubuntu user that installed the shadow"
# Enable systemd's user manager to remain available when the SSH/Cloud Shell session exits.
sudo loginctl enable-linger "$user"
linger="$(loginctl show-user "$user" -p Linger --value 2>/dev/null || true)"
[[ "$linger" == yes ]] || fail "User linger could not be enabled"
systemctl --user daemon-reload
systemctl --user enable --now "$unit"
systemctl --user is-enabled --quiet "$unit" || fail "Service not enabled at login/boot"
systemctl --user is-active --quiet "$unit" || fail "Service not active"
# Verify the running service is really the pinned read-only SDK and not some other process.
"$base/current/node/bin/node" --input-type=module -e '
  const url="http://127.0.0.1:48721/health";
  const response=await fetch(url,{signal:AbortSignal.timeout(5000)});
  if(!response.ok)process.exit(1);
  const d=await response.json();
  if(d.sdkVersion!=="0.5.0"||d.mode!=="shadow-read-only"||d.writesEnabled!==false||d.loginAllowed!==false)process.exit(2);
' || fail "Read-only SDK health check failed; original lights unchanged"
echo "PERMANENT_READY: user=$user linger=yes enabled=yes active=yes SDK=0.5.0 read-only; survives SSH logout and reboot."
