#!/usr/bin/env bash
# 달림 하네스 설치 — 저장소 루트에서 1회 실행
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "== 달림 하네스 설치 =="

# 1) 필수 구조 확인
for f in CLAUDE.md .claude/skills/dallim-orchestrator/SKILL.md docs/01-feature-spec.md docs/04-ui-guide.md; do
  [ -f "$f" ] || { echo "  [!] 누락: $f"; exit 1; }
done
echo "  [v] 하네스 파일 확인"

# 2) 프로젝트 폴더 생성
for d in backend android; do
  if [ -d "$d" ]; then echo "  [v] $d/ already exists"; else mkdir -p "$d"; echo "  [+] $d/ 생성"; fi
done

# 3) 환경변수 템플릿
if [ ! -f .env ]; then
  cp .env.example .env 2>/dev/null && echo "  [+] .env 생성 (값 채워주세요)" || true
fi

# 4) 도구 확인
command -v docker >/dev/null 2>&1 && echo "  [v] docker" || echo "  [!] docker 없음 — scripts/dev-db.sh 사용 불가"
command -v java   >/dev/null 2>&1 && echo "  [v] java"   || echo "  [!] java 없음 — JDK 17+ 필요"

cat <<'EOF'

설치 완료.

다음 단계
  1. .env 값 채우기 (DB/JWT/OAuth 키)
  2. ./scripts/dev-db.sh up      # PostGIS + Redis 기동
  3. 저장소 루트에서 claude 실행 후:
       "architect 에이전트로 backend/android 초기 구조 만들어줘"

EOF
