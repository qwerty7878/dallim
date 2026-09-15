"""LangGraph 그래프 정의 -- 신고 하나를 fetch_context -> classify -> persist -> (HIGH면) notify_discord
순서로 처리한다. 모든 신고는 persist까지 공통으로 거치고, notify_discord는 severity == HIGH일 때만
조건부 엣지로 탄다.
"""
from typing import Optional, TypedDict

from langgraph.graph import END, StateGraph

import classify
import db
import discord_client


class TriageState(TypedDict, total=False):
    report_id: str
    target_type: str
    target_id: str
    reporter_user_id: str
    reason: Optional[str]
    context_text: str
    category: str
    severity: str
    summary: str


def fetch_context_node(state: TriageState) -> dict:
    context_text = db.fetch_context(state["target_type"], state["target_id"])
    return {"context_text": context_text}


def classify_node(state: TriageState) -> dict:
    return classify.classify(state.get("reason"), state["context_text"])


def persist_node(state: TriageState) -> dict:
    db.persist_triage(state["report_id"], state["category"], state["severity"], state["summary"])
    return {}


def route_by_severity(state: TriageState) -> str:
    return "notify" if state["severity"] == "HIGH" else "end"


def notify_discord_node(state: TriageState) -> dict:
    sent = discord_client.notify_high_severity_report(
        report_id=state["report_id"],
        target_type=state["target_type"],
        target_id=state["target_id"],
        category=state["category"],
        summary=state["summary"],
        context_text=state["context_text"],
    )
    if sent:
        db.mark_notified(state["report_id"])
    return {}


def build_graph():
    graph = StateGraph(TriageState)
    graph.add_node("fetch_context", fetch_context_node)
    graph.add_node("classify", classify_node)
    graph.add_node("persist", persist_node)
    graph.add_node("notify_discord", notify_discord_node)

    graph.set_entry_point("fetch_context")
    graph.add_edge("fetch_context", "classify")
    graph.add_edge("classify", "persist")
    graph.add_conditional_edges("persist", route_by_severity, {"notify": "notify_discord", "end": END})
    graph.add_edge("notify_discord", END)

    return graph.compile()
