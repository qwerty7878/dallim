# 달림 하네스

Claude Code가 `docs/` SPEC을 기준으로 달림 MVP1을 구현하도록 구성한 하네스입니다.

## 구조

```
dallim/                         ← 저장소 루트 (이 zip을 여기에 품)
 ├─ CLAUDE.md                    프로젝트 컨텍스트 + 공통 규칙
 ├─ .claude/
 │   ├─ agents/                   architect · ui-designer · backend-dev · android-dev · qa-engineer
 │   └─ skills/dallim-orchestrator/SKILL.md
 ├─ docs/
 │   ├─ 01-feature-spec.md        기능 명세
 │   ├─ 02-api-spec.md            API 명세 (로그인 3종 포함)
 │   ├─ 03-design-system.md       디자인 토큰 값
 │   └─ 04-ui-guide.md            UI 배치 규칙 ★ 디자인 품질의 핵심
 ├─ scripts/
 │   ├─ install.sh                최초 1회 설치
 │   ├─ dev-db.sh                 PostGIS + Redis 기동 (up/down/reset/logs)
 │   ├─ dev-backend.sh            Ktor 서버 실행
 │   └─ gate-check.sh             커밋 전 게이트 (빌드·테스트·UI규칙·민감정보 검사)
 ├─ docker-compose.yml
 ├─ .env.example
 ├─ backend/                      ← Ktor (IntelliJ에서 이 폴더만 열기)
 └─ android/                      ← Android Studio에서 이 폴더만 열기
```

## 시작하기

```bash
unzip dallim-harness.zip -d .     # 저장소 루트에 풀기
./scripts/install.sh              # 구조 확인 + backend/ android/ 생성
cp .env.example .env              # 값 채우기
./scripts/dev-db.sh up            # DB 기동
claude                            # 저장소 루트에서 Claude Code 실행
```

이후 자연어로 요청하면 `dallim-orchestrator`가 알맞은 에이전트를 호출합니다.

```
"architect로 backend/android 초기 구조 만들어줘"
"로그인 API 3종 구현해줘"          → backend-dev
"홈 화면 만들어줘"                 → ui-designer → android-dev
"이 화면 디자인이 이상한데 봐줘"     → ui-designer (04-ui-guide §0 진단표로 원인 특정)
```

## 게이트

커밋 전 `./scripts/gate-check.sh`를 실행하면 다음을 자동 검사합니다.

- 백엔드 빌드 + 테스트 통과 여부
- 안드로이드 컴파일 여부
- **UI 가이드 위반** — 하드코딩된 `Color(0x...)`, 8dp 배수 아닌 `.dp` 값
- **민감정보 노출** — 응답 DTO의 `gender`, 비밀번호 로깅
