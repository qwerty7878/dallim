#!/usr/bin/env bash
# 로컬 개발 DB(PostGIS + Redis) + OSRM 라우팅 엔진 기동/정지
# OSRM 라우팅 데이터가 아직 없으면(osrm-data/*.osrm) 먼저 scripts/osrm-build.sh를 실행해라 —
# 이 스크립트는 이미 만들어진 데이터를 osrm-routed로 띄우기만 한다.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

case "${1:-up}" in
  up)
    docker compose up -d
    echo "PostGIS  : localhost:5432 (db=dallim user=dallim pw=dallim)"
    echo "Redis    : localhost:6379"
    if [ -f osrm-data/south-korea.osrm ]; then
      echo "OSRM     : localhost:5001 (foot/보행자 라우팅 — /route/v1/foot/..., /match/v1/foot/...)"
    else
      echo "OSRM     : 데이터 없음 — scripts/osrm-build.sh 먼저 실행하면 osrm 컨테이너도 뜬다"
    fi
    ;;
  down) docker compose down ;;
  reset)
    read -p "DB 데이터를 전부 삭제합니다. 계속? (y/N) " a
    [ "$a" = "y" ] && docker compose down -v && docker compose up -d && echo "초기화 완료"
    ;;
  logs) docker compose logs -f ;;
  *) echo "usage: $0 {up|down|reset|logs}"; exit 1 ;;
esac
