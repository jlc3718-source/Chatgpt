#!/usr/bin/env bash
# Install isolated SDK 0.5.0 shadow only. Does NOT change the existing JH2 app.
set -Eeuo pipefail
umask 077
base="$HOME/.local/share/jh2-eufy-mega-shadow"
release="$base/releases/node24-sdk050-v1"
commit="4409ac8511320affca7a713793eb021e6ee4f617"
raw="https://raw.githubusercontent.com/jlc3718-source/Chatgpt/$commit/experiments/eufy-mega-shadow"
if [[ -e "$base/current" || -L "$base/current" ]]; then echo "SHADOW_ALREADY_INSTALLED"; exit 0; fi
for cmd in curl tar sha256sum mktemp; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 2; }; done
case "$(uname -m)" in x86_64) arch=x64;; aarch64|arm64) arch=arm64;; *) echo "Unsupported architecture" >&2; exit 2;; esac
mkdir -p "$base/releases" "$HOME/.config/systemd/user"
work="$(mktemp -d "$base/.build.XXXXXXXX")"
pid=""
cleanup(){ [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true; [[ -n "$work" && -d "$work" ]] && rm -rf "$work"; }
trap cleanup EXIT
nodefile="node-v24.5.0-linux-$arch.tar.xz"
nodeurl="https://nodejs.org/dist/v24.5.0"
curl -fsSL --retry 2 "$nodeurl/SHASUMS256.txt" -o "$work/SHASUMS256.txt"
curl -fsSL --retry 2 "$nodeurl/$nodefile" -o "$work/$nodefile"
(cd "$work"; grep "  $nodefile\$" SHASUMS256.txt | sha256sum --check --status)
mkdir -p "$work/node" "$work/private"
tar -xJf "$work/$nodefile" --strip-components=1 -C "$work/node"
rm "$work/$nodefile" "$work/SHASUMS256.txt"
for file in package.json shadow.mjs; do curl -fsSL --retry 2 "$raw/$file" -o "$work/$file"; done
export PATH="$work/node/bin:$PATH"
(cd "$work"; npm install --omit=dev --no-audit --no-fund)
"$work/node/bin/node" --check "$work/shadow.mjs"
token="$("$work/node/bin/node" -p "require('node:crypto').randomBytes(32).toString('hex')")"
printf 'JH2_MEGA_SHADOW_PORT=48721\nJH2_MEGA_ALLOW_LOGIN=0\nJH2_MEGA_SHADOW_TOKEN=%s\nJH2_MEGA_SESSION_FILE=%s/private/.eufy-shadow-session.json\n' "$token" "$release" > "$work/.env"
echo 'JH2_MEGA_SHADOW_INSTALLED_V1' > "$work/.jh2-shadow-installed"
(cd "$work"; exec ./node/bin/node --env-file=.env shadow.mjs > "$work/smoke.log" 2>&1) & pid=$!
ready=0
for i in $(seq 1 25); do
 if curl -fsS --max-time 2 http://127.0.0.1:48721/health > "$work/health.json" 2>/dev/null; then ready=1; break; fi
 sleep 1
done
[[ "$ready" -eq 1 ]] || { echo "SHADOW_SMOKE_FAILED: production unchanged" >&2; exit 1; }
"$work/node/bin/node" -e 'const h=require(process.argv[1]);if(h.loginAllowed||h.writesEnabled!==false||h.sdkVersion!=="0.5.0")process.exit(1)' "$work/health.json"
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
pid=""
[[ ! -e "$release" ]] || { echo "Release directory already exists" >&2; exit 2; }
mv "$work" "$release"
work=""
ln -s "$release" "$base/current"
unit="$HOME/.config/systemd/user/jh2-eufy-mega-shadow.service"
cat > "$unit" <<EOF
[Unit]
Description=JH2 Eufy Mega 0.5.0 isolated read-only shadow
After=network-online.target
[Service]
Type=simple
WorkingDirectory=$release
ExecStart=$release/node/bin/node --env-file=$release/.env $release/shadow.mjs
Restart=on-failure
RestartSec=15
NoNewPrivileges=yes
[Install]
WantedBy=default.target
EOF
if command -v systemctl >/dev/null && systemctl --user daemon-reload >/dev/null 2>&1 && systemctl --user enable --now jh2-eufy-mega-shadow.service >/dev/null 2>&1; then
 echo "SHADOW_READY: systemd user service, 127.0.0.1:48721, login OFF, lights unchanged"
else
 systemctl --user disable --now jh2-eufy-mega-shadow.service >/dev/null 2>&1 || true
 (cd "$release"; nohup ./node/bin/node --env-file=.env shadow.mjs > service.log 2>&1 < /dev/null & echo $! > "$base/shadow.pid")
 echo "SHADOW_READY: background process, 127.0.0.1:48721, login OFF, lights unchanged"
fi
