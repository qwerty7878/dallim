#!/usr/bin/env bash
# 코스 드로잉(직접 그리기)/AI 자동 생성용 OSRM 보행자(foot) 라우팅 데이터 빌드.
#
# 네이버 Directions API(5/15)는 자동차 전용이라 도보 코스 생성에 쓸 수 없다(2026-09-03 확인)
# — 대신 OpenStreetMap 데이터를 OSRM으로 직접 처리해서 자체 호스팅한다. 일회성/데이터 갱신용
# 스크립트이며, 결과물(osrm-data/*.osrm*)은 gitignore되어 있고 docker-compose.yml의 osrm
# 서비스가 그대로 마운트해서 osrm-routed로 서빙한다 (scripts/dev-db.sh up으로 기동).
#
# 재실행하면 OSM 데이터를 다시 받아 처음부터 다시 처리한다(지도 갱신 시 그대로 재사용).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DATA_DIR="$ROOT/osrm-data"
PROFILE_DIR="$ROOT/scripts/osrm-profiles"
PBF="$DATA_DIR/south-korea.osm.pbf"
OSRM_FILE="$DATA_DIR/south-korea.osrm"
# Geofabrik south-korea-latest.osm.pbf는 날짜가 박힌 실제 파일로 리다이렉트된다 — latest만 받으면 됨.
SOURCE_URL="https://download.geofabrik.de/asia/south-korea-latest.osm.pbf"

mkdir -p "$DATA_DIR"

if [ ! -f "$PBF" ] || [ "${1:-}" = "--fresh" ]; then
  echo "[osrm-build] Downloading OSM extract from Geofabrik..."
  curl -L -o "$PBF" "$SOURCE_URL"
fi

# 표준 OSRM foot 프로필이 아니라, 도로 안전성 기준(docs/01-feature-spec.md 2.2.C)에 맞춰 인도/
# 공원 보행로를 우대하고 인도 없는 큰길을 기피하도록 커스텀한 프로필을 쓴다
# (scripts/osrm-profiles/dallim-foot.lua, docs/02-api-spec.md 8.2).
echo "[osrm-build] osrm-extract (dallim-foot profile)..."
docker run --rm -v "$DATA_DIR:/data" -v "$PROFILE_DIR:/profiles" osrm/osrm-backend \
  osrm-extract -p /profiles/dallim-foot.lua /data/south-korea.osm.pbf

echo "[osrm-build] osrm-partition (MLD)..."
docker run --rm -v "$DATA_DIR:/data" osrm/osrm-backend \
  osrm-partition /data/south-korea.osrm

echo "[osrm-build] osrm-customize (MLD)..."
docker run --rm -v "$DATA_DIR:/data" osrm/osrm-backend \
  osrm-customize /data/south-korea.osrm

echo "[osrm-build] Done. Start the routing server with: scripts/dev-db.sh up"
echo "[osrm-build] Test: curl 'http://localhost:5001/route/v1/foot/126.9235,37.3905;126.9268,37.3928?overview=false'"
