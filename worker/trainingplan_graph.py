"""LangGraph 그래프 정의 -- 훈련 플랜 하나를 fetch_context -> build_plan_skeleton ->
match_courses -> personalize_comment -> persist 순서로 처리한다.

신고 트리아지(`graph.py`)와 달리 조건부 분기가 없다 -- 모든 플랜이 같은 5단계를 그대로 거친다.
이 그래프가 예외를 던지면(어느 노드에서든) `trainingplan_main.py`의 컨슈머 루프가 잡아서
`training_plans.status`를 FAILED로 기록한다 -- 그래서 각 노드는 실패를 여기서 삼키지 않고
그대로 위로 던진다(단, LLM 코멘트 노드만 예외: `trainingplan_classify.personalize_comment`
자체가 3회 재시도 후 정적 폴백으로 절대 예외를 던지지 않는다 -- 코멘트 하나 못 받았다고 플랜
전체를 FAILED로 만들 이유는 없다는 판단).
"""
import math
from datetime import datetime, timezone
from typing import Optional, TypedDict

from langgraph.graph import END, StateGraph

import plan_skeleton
import trainingplan_classify
import trainingplan_db


class TrainingPlanState(TypedDict, total=False):
    plan_id: str
    user_id: str
    race_id: str
    race_category: str
    race_date_iso: str
    race_context: dict
    recent_avg_weekly_km: Optional[float]
    recent_avg_pace_sec_per_km: Optional[float]
    weeks_until_race: int
    weeks: list[dict]
    comment: str


def fetch_context_node(state: TrainingPlanState) -> dict:
    race_context = trainingplan_db.fetch_race_context(state["race_id"], state["race_category"])
    if race_context is None:
        raise RuntimeError(
            f"대회/종목 정보를 찾을 수 없음 (race_id={state['race_id']}, category={state['race_category']})"
        )
    recent_avg_weekly_km, recent_avg_pace_sec_per_km = trainingplan_db.fetch_recent_avg_weekly_km(state["user_id"])
    return {
        "race_context": race_context,
        "recent_avg_weekly_km": recent_avg_weekly_km,
        "recent_avg_pace_sec_per_km": recent_avg_pace_sec_per_km,
    }


def build_plan_skeleton_node(state: TrainingPlanState) -> dict:
    race_date = state["race_context"]["race_date"]
    now = datetime.now(timezone.utc)
    days_until_race = (race_date - now).days
    # 이미 지난 대회거나 이번 주가 대회 주여도 최소 1주로 취급한다 --
    # plan_skeleton.build_weeks 자체도 방어적으로 clamp하지만, 음수 주 계산을 피하려고 여기서도 한 번 더.
    weeks_until_race = max(1, math.ceil(days_until_race / 7))

    weeks = plan_skeleton.build_weeks(
        weeks_until_race=weeks_until_race,
        target_distance_km=state["race_context"]["distance_km"],
        recent_avg_weekly_km=state.get("recent_avg_weekly_km"),
    )
    return {"weeks": weeks, "weeks_until_race": weeks_until_race}


def match_courses_node(state: TrainingPlanState) -> dict:
    weeks = state["weeks"]
    for week in weeks:
        for session in week["sessions"]:
            target_km = session.get("target_distance_km")
            if session["type"] == "REST" or not target_km:
                session["matched_route_id"] = None
                continue
            matched = trainingplan_db.match_route_for_distance(target_km)
            session["matched_route_id"] = matched["id"] if matched else None
    return {"weeks": weeks}


def personalize_comment_node(state: TrainingPlanState) -> dict:
    race_context = state["race_context"]
    peak_weekly_km = max(
        (
            sum(s.get("target_distance_km") or 0.0 for s in week["sessions"])
            for week in state["weeks"]
        ),
        default=0.0,
    )
    llm_context = {
        "race_name": race_context["race_name"],
        "category": state["race_category"],
        "race_date": race_context["race_date"],
        "target_distance_km": race_context["distance_km"],
        "weeks_until_race": state["weeks_until_race"],
        "peak_weekly_target_distance_km": round(peak_weekly_km, 1),
        "recent_avg_weekly_km": state.get("recent_avg_weekly_km"),
        "recent_avg_pace_sec_per_km": state.get("recent_avg_pace_sec_per_km"),
    }
    comment = trainingplan_classify.personalize_comment(llm_context)
    return {"comment": comment}


def persist_node(state: TrainingPlanState) -> dict:
    trainingplan_db.persist_ready_plan(state["plan_id"], state["weeks"], state["comment"])
    return {}


def build_graph():
    graph = StateGraph(TrainingPlanState)
    graph.add_node("fetch_context", fetch_context_node)
    graph.add_node("build_plan_skeleton", build_plan_skeleton_node)
    graph.add_node("match_courses", match_courses_node)
    graph.add_node("personalize_comment", personalize_comment_node)
    graph.add_node("persist", persist_node)

    graph.set_entry_point("fetch_context")
    graph.add_edge("fetch_context", "build_plan_skeleton")
    graph.add_edge("build_plan_skeleton", "match_courses")
    graph.add_edge("match_courses", "personalize_comment")
    graph.add_edge("personalize_comment", "persist")
    graph.add_edge("persist", END)

    return graph.compile()
