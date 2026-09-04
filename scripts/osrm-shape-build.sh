#!/usr/bin/env bash
# 모양 선택(SHAPE) AI 생성 전용 OSRM 데이터 빌드 — docs/02-api-spec.md 13.2.
#
# scripts/osrm-build.sh(전국, 8장/11장의 LOOP/POINT_TO_POINT용)와 별개로, 서비스 벨트(안양·
# 군포·의왕·과천·성남 분당/판교)만 잘라낸 훨씬 작은 데이터셋을 dallim-foot-shape.lua(대로변
# 인도 우선 프로필)로 빌드한다 — Lightsail t3.micro급(RAM 1GB)에 전국 데이터셋(~2.8GB)을 두
# 벌 못 올린다.
#
# 이미 받아둔 osrm-data/south-korea.osm.pbf를 재사용하고(재다운로드 없음), osmium extract로
# bbox만 잘라낸 뒤 osrm-extract/partition/customize(MLD)를 돌린다. 먼저 scripts/osrm-build.sh를
# 한 번 실행해 south-korea.osm.pbf를 받아둬야 한다.
#
# bbox는 1차 추정치다 — 안양(37.39/126.95), 군포(37.36/126.93), 의왕(37.34/126.97),
# 과천(37.43/126.99), 성남 분당·판교(37.35~37.40/127.10~127.13) 좌표를 기준으로 여유를 두고
# 잡았다. 실제 지도로 5개 지역을 모두 포함하는지 확인 후 필요하면 아래 환경변수로 조정해라
# (예: BBOX_MAX_LAT=37.50 ./scripts/osrm-shape-build.sh).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DATA_DIR="$ROOT/osrm-data"
PROFILE_DIR="$ROOT/scripts/osrm-profiles"
SOURCE_PBF="$DATA_DIR/south-korea.osm.pbf"
BELT_PBF="$DATA_DIR/dallim-belt.osm.pbf"

# 서비스 벨트 bounding box: min_lon,min_lat,max_lon,max_lat (osmium extract -b 순서).
BBOX_MIN_LON="${BBOX_MIN_LON:-126.83}"
BBOX_MIN_LAT="${BBOX_MIN_LAT:-37.28}"
BBOX_MAX_LON="${BBOX_MAX_LON:-127.17}"
BBOX_MAX_LAT="${BBOX_MAX_LAT:-37.47}"

if [ ! -f "$SOURCE_PBF" ]; then
  echo "[osrm-shape-build] $SOURCE_PBF 없음 — 먼저 scripts/osrm-build.sh를 실행해 전국 pbf를 받아둬야 한다." >&2
  exit 1
fi

echo "[osrm-shape-build] osmium extract (bbox ${BBOX_MIN_LON},${BBOX_MIN_LAT},${BBOX_MAX_LON},${BBOX_MAX_LAT})..."
docker run --rm -v "$DATA_DIR:/data" osmium/osmium-tool osmium extract \
  -b "${BBOX_MIN_LON},${BBOX_MIN_LAT},${BBOX_MAX_LON},${BBOX_MAX_LAT}" \
  --overwrite -o /data/dallim-belt.osm.pbf /data/south-korea.osm.pbf

# SHAPE 모드 전용 프로필(scripts/osrm-profiles/dallim-foot-shape.lua) — dallim-foot.lua와 달리
# 대로변+인도를 최우대하고 공원길/보행로는 중립으로 낮춘다(13.2 표 참고).
echo "[osrm-shape-build] osrm-extract (dallim-foot-shape profile)..."
docker run --rm -v "$DATA_DIR:/data" -v "$PROFILE_DIR:/profiles" osrm/osrm-backend \
  osrm-extract -p /profiles/dallim-foot-shape.lua /data/dallim-belt.osm.pbf

echo "[osrm-shape-build] osrm-partition (MLD)..."
docker run --rm -v "$DATA_DIR:/data" osrm/osrm-backend \
  osrm-partition /data/dallim-belt.osrm

echo "[osrm-shape-build] osrm-customize (MLD)..."
docker run --rm -v "$DATA_DIR:/data" osrm/osrm-backend \
  osrm-customize /data/dallim-belt.osrm

echo "[osrm-shape-build] Done. Start the routing server with: scripts/dev-db.sh up"
echo "[osrm-shape-build] Test: curl 'http://localhost:5002/route/v1/foot/126.9235,37.3905;126.9400,37.3950?overview=false'"
