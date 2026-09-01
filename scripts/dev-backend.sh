#!/usr/bin/env bash
# Ktor 백엔드 실행
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/backend"
[ -f gradlew ] || { echo "[!] backend/gradlew 없음 — architect 에이전트로 프로젝트를 먼저 생성하세요"; exit 1; }
set -a; [ -f "$ROOT/.env" ] && . "$ROOT/.env"; set +a
./gradlew run
