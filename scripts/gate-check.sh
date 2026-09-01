#!/usr/bin/env bash
# 커밋 전 게이트 — 실패 시 즉시 중단
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
FAIL=0
step() { echo ""; echo "── $1"; }

step "1/4 백엔드 빌드 + 테스트"
if [ -f backend/gradlew ]; then
  (cd backend && ./gradlew build test) || FAIL=1
else echo "  (skip) backend 미생성"; fi

step "2/4 안드로이드 컴파일"
if [ -f android/gradlew ]; then
  (cd android && ./gradlew assembleDebug) || FAIL=1
else echo "  (skip) android 미생성"; fi

step "3/4 UI 가이드 위반 검사 (하드코딩 색상/간격)"
if [ -d android ]; then
  HITS=$(grep -rnE "Color\(0x|[^a-zA-Z_](7|9|11|13|15|17|19|21|23)\.dp" android --include=*.kt \
         | grep -v "core-ui" || true)
  if [ -n "$HITS" ]; then
    echo "  [!] 토큰 대신 하드코딩된 값 발견 (docs/04-ui-guide.md §10 위반):"
    echo "$HITS" | head -20
    FAIL=1
  else echo "  [v] 통과"; fi
fi

step "4/4 민감정보 노출 검사 (gender / password)"
if [ -d backend ]; then
  LEAK=$(grep -rn "gender" backend --include=*.kt | grep -iE "response|dto" || true)
  [ -n "$LEAK" ] && { echo "  [!] 응답 DTO에 gender 노출 의심:"; echo "$LEAK"; FAIL=1; } || echo "  [v] gender 미노출"
  PLEAK=$(grep -rniE "(println|log).*(password|passwordHash)" backend --include=*.kt || true)
  [ -n "$PLEAK" ] && { echo "  [!] 비밀번호 로깅 의심:"; echo "$PLEAK"; FAIL=1; } || echo "  [v] 비밀번호 로깅 없음"
fi

echo ""
[ $FAIL -eq 0 ] && echo "✅ 게이트 통과" || { echo "❌ 게이트 실패 — 위 항목을 수정하세요"; exit 1; }
