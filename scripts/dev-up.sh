#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -d node_modules ]; then
  npm ci
fi

npm run build:css

docker compose up -d --build --remove-orphans db app
docker compose up -d --force-recreate cloudflared

CLOUDFLARE_URL="$(python3 - <<'PY'
import re
import subprocess
import time

pattern = re.compile(r'https://[-a-zA-Z0-9]+\.trycloudflare\.com')

for _ in range(60):
    logs = subprocess.run(
        ['docker', 'compose', 'logs', '--no-color', 'cloudflared'],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    match = pattern.search(logs)
    if match:
        print(match.group(0))
        raise SystemExit(0)
    time.sleep(1)

raise SystemExit('Cloudflare tunnel did not become ready')
PY
)"

python3 - "$CLOUDFLARE_URL" <<'PY'
from pathlib import Path
import sys

url = sys.argv[1].rstrip('/')
path = Path('.env')
lines = path.read_text(encoding='utf-8').splitlines()
app_port = '8080'
for line in lines:
    if line.startswith('APP_PORT='):
        app_port = line.split('=', 1)[1].strip().strip('"\'') or app_port
        break
values = {
    'VNPAY_RETURN_URL': f'http://localhost:{app_port}/payments/vnpay/return',
    'VNPAY_IPN_URL': f'{url}/api/v1/payments/vnpay/ipn',
}

seen = set()
out = []
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line and not line.lstrip().startswith('#') else None
    if key in values:
        out.append(f'{key}={values[key]}')
        seen.add(key)
    else:
        out.append(line)

for key, value in values.items():
    if key not in seen:
        out.append(f'{key}={value}')

path.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY

docker compose up -d --force-recreate app

echo
echo "StayHub is running: http://localhost:${APP_PORT:-8080}"
echo "Cloudflare public URL: $CLOUDFLARE_URL"
echo "VNPay browser return URL: http://localhost:${APP_PORT:-8080}/payments/vnpay/return"
echo "VNPay IPN URL: $CLOUDFLARE_URL/api/v1/payments/vnpay/ipn"
echo "Configure this IPN URL in the VNPay sandbox merchant settings and keep cloudflared running while testing."
echo
echo "Logs: docker compose logs -f app cloudflared"
echo "Stop: ./scripts/dev-stop.sh"
