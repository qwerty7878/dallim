---
name: dallim-orchestrator
description: "달림(GPS 그림 러닝 앱) 프로젝트의 에이전트 팀을 조율하는 오케스트레이터. 로그인(Google/Kakao), 코스 조회, GPS 트래킹, 완주 판정, 러닝 결과, 달림북, 백엔드 API 구현, 안드로이드 화면 구현 관련 작업 요청 시 이 스킬을 사용하라. 후속 작업: 결과 수정, 부분 재실행, 업데이트, 보완, 다시 실행, 특정 모듈만 재구현, 이전 결과 개선, 버그 수정 요청 시에도 반드시 이 스킬을 사용."
---

# 달림 Orchestrator

`docs/` 하위 SPEC 문서를 기준으로 백엔드/안드로이드 에이전트 팀을 조율해
MVP1(온보딩→코스탐색→러닝→결과→달림북)을 구현하는 통합 스킬.

## 실행 모드: 에이전트 팀

## 에이전트 구성

| 팀원 | 에이전트 정의 | 역할 | 출력 |
|------|-------------|------|------|
| architect | `.claude/agents/architect.md` | backend/android 초기 구조, 엔티티, 디자인 토큰 이식 | `backend/`, `android/` scaffolding |
| backend-dev | `.claude/agents/backend-dev.md` | API 구현, 완주 판정 로직 | `backend/src/main/...` |
| android-dev | `.claude/agents/android-dev.md` | 화면 구현, GPS 트래킹 파이프라인 | `android/app/src/main/...` |
| qa-engineer | `.claude/agents/qa-engineer.md` | 완주 판정 단위 테스트, API 통합 테스트 | `backend/src/test/...`, `docs/qa-checklist.md` |

## SPEC 참조

모든 에이전트는 저장소 루트의 다음 문서를 기준 문서로 사용한다.

- `docs/01-feature-spec.md` — 화면별/도메인별 기능 명세
- `docs/02-api-spec.md` — REST API 요청/응답 스키마
- `docs/03-design-system.md` — 컬러/타이포/컴포넌트 토큰

## 실행 순서 (기본)

1. **architect** — 아직 프로젝트 뼈대가 없으면 먼저 실행 (backend/android 둘 다 또는 필요한 쪽만)
2. **backend-dev** ↔ **android-dev** — SPEC의 도메인 순서(인증→코스→러닝→달림북)를 따라
   병렬 또는 순차로 실행. 한쪽만 요청된 작업이면 그쪽만 실행.
3. **qa-engineer** — backend-dev의 완주 판정 로직이 변경/신규 구현될 때마다 반드시 뒤이어 실행

## 위임 규칙

- "백엔드/서버/API" 관련 요청 → backend-dev 단독 (필요 시 architect 선행)
- "안드로이드/화면/UI/GPS" 관련 요청 → android-dev 단독 (필요 시 architect 선행)
- "로그인부터 러닝까지 전체 구현해줘" 같은 포괄적 요청 → architect → backend-dev + android-dev → qa-engineer 순으로 전체 실행
- 완주 판정 로직을 건드리는 모든 작업 뒤에는 qa-engineer를 반드시 호출한다 (생략 금지)
