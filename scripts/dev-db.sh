#!/usr/bin/env bash
# 로컬 개발 DB(PostGIS + Redis) 기동/정지
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

case "${1:-up}" in
  up)
    docker compose up -d
    echo "PostGIS  : localhost:5432 (db=dallim user=dallim pw=dallim)"
    echo "Redis    : localhost:6379"
    ;;
  down) docker compose down ;;
  reset)
    read -p "DB 데이터를 전부 삭제합니다. 계속? (y/N) " a
    [ "$a" = "y" ] && docker compose down -v && docker compose up -d && echo "초기화 완료"
    ;;
  logs) docker compose logs -f ;;
  *) echo "usage: $0 {up|down|reset|logs}"; exit 1 ;;
esac
