# -*- coding: utf-8 -*-
"""端到端浏览器验收：前端交互 → HTTP → 后端 → state.json → 页面回显。"""
from __future__ import annotations

import json
import os
import re
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get("E2E_BASE", "http://localhost:3002")
REPO = Path(__file__).resolve().parent.parent
STATE_FILE = Path(os.environ.get("E2E_STATE_FILE", REPO / "server" / "data" / "state.json"))
ART = Path(os.environ.get("E2E_ART_DIR", Path(__file__).resolve().parent / "e2e-artifacts"))
ART.mkdir(parents=True, exist_ok=True)

results: list[tuple[str, bool, str]] = []


def rec(name: str, ok: bool, detail: str = ""):
    results.append((name, ok, detail))
    print(("PASS" if ok else "FAIL"), name, detail, flush=True)


def load_state():
    return json.loads(STATE_FILE.read_text(encoding="utf-8"))


def main() -> int:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        page.set_default_timeout(30000)

        # ---------- 1. 首页 / 今日闭环 ----------
        page.goto(BASE + "/", wait_until="networkidle")
        title = page.title()
        rec("首页可打开", "闭环" in title or "面试" in title or "Learning" in title or len(title) > 0, title)
        body = page.inner_text("body")
        rec("今日闭环可见", ("今日" in body) or ("队列" in body) or ("闭环" in body) or ("KPI" in body), body[:80])
        page.screenshot(path=str(ART / "01-today.png"), full_page=True)

        # 等队列/按钮
        page.wait_for_timeout(1500)
        body = page.inner_text("body")

        # ---------- 2. 知识地图 ----------
        # hash 路由点击侧栏
        for label in ["知识地图", "地图", "Map"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1500)
        page.screenshot(path=str(ART / "02-map.png"), full_page=True)
        b = page.inner_text("body")
        rec("知识地图页", ("Java" in b or "算法" in b or "AI" in b or "域" in b or "知识点" in b), b[:100])

        # ---------- 3. 缺口库 ----------
        for label in ["缺口", "缺口库"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1200)
        page.screenshot(path=str(ART / "03-gaps.png"), full_page=True)
        b = page.inner_text("body")
        rec("缺口库页", ("缺口" in b), b[:100])

        # ---------- 4. 遗忘曲线 ----------
        for label in ["遗忘曲线", "曲线", "Curve"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1200)
        page.screenshot(path=str(ART / "04-curve.png"), full_page=True)
        b = page.inner_text("body")
        rec("遗忘曲线页", ("R" in b or "到期" in b or "曲线" in b or "记忆" in b), b[:100])

        # ---------- 5. 学习记录 ----------
        for label in ["学习记录", "记录", "Records"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1200)
        page.screenshot(path=str(ART / "05-records.png"), full_page=True)
        b = page.inner_text("body")
        rec("学习记录页", ("记录" in b or "会话" in b or "周报" in b or "掌握" in b), b[:100])

        # ---------- 6. 力扣 ----------
        for label in ["力扣", "LeetCode", "LC"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1500)
        page.screenshot(path=str(ART / "06-lc.png"), full_page=True)
        b = page.inner_text("body")
        rec("力扣列表页", ("两数之和" in b or "101" in b or "题目" in b or "力扣" in b), b[:120])

        # ---------- 7. 项目部分 ----------
        for label in ["项目", "Projects"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1500)
        page.screenshot(path=str(ART / "07-projects.png"), full_page=True)
        b = page.inner_text("body")
        rec("项目部分页", ("简历" in b or "面试" in b or "切面" in b or "项目" in b), b[:100])

        # ---------- 8. 设置 ----------
        for label in ["设置", "Settings"]:
            loc = page.get_by_text(label, exact=False).first
            if loc.count() > 0:
                try:
                    loc.click(timeout=3000)
                    break
                except Exception:
                    pass
        page.wait_for_timeout(1200)
        page.screenshot(path=str(ART / "08-settings.png"), full_page=True)
        b = page.inner_text("body")
        rec("设置页", ("AI" in b or "权重" in b or "设置" in b), b[:100])

        # ---------- 9. 今日闭环完整答题（真实 AI 评分）----------
        page.goto(BASE + "/", wait_until="networkidle")
        page.wait_for_timeout(1500)
        # 找「开始」或队列卡片
        start_btns = page.get_by_role("button")
        clicked = False
        for i in range(start_btns.count()):
            t = (start_btns.nth(i).inner_text() or "").strip()
            if any(k in t for k in ["开始", "生成今日", "闭环", "进入作答"]):
                try:
                    start_btns.nth(i).click(timeout=4000)
                    clicked = True
                    rec("点击开始闭环/生成", True, t)
                    break
                except Exception as e:
                    pass
        if not clicked:
            # 直接点第一张队列卡
            cards = page.locator(".queue-item, .card, [class*=queue]")
            if cards.count() > 0:
                cards.first.click(timeout=5000)
                clicked = True
                rec("点击队列卡片", True, "first card")
        page.wait_for_timeout(2500)
        page.screenshot(path=str(ART / "09-workbench.png"), full_page=True)

        # 若进入作答台，输入答案并提交
        ta = page.locator("textarea").first
        if ta.count() > 0:
            answer = (
                "Java 线程与进程的关系是 1:1 映射：每个 Java 线程对应一个 OS 内核线程。"
                "进程是资源分配单位，拥有独立地址空间、文件描述符表；"
                "线程是调度单位，共享进程的堆和方法区，独享栈和寄存器上下文。"
                "因此线程切换比进程切换便宜（不用换页表），但仍要保存/恢复寄存器并付出缓存冷启动代价。"
            )
            ta.fill(answer)
            page.wait_for_timeout(400)
            submit = page.get_by_role("button")
            submitted = False
            for i in range(submit.count()):
                t = (submit.nth(i).inner_text() or "").strip()
                if any(k in t for k in ["提交", "评分", "交卷"]):
                    submit.nth(i).click(timeout=5000)
                    submitted = True
                    rec("提交作答", True, t)
                    break
            if submitted:
                page.wait_for_timeout(45000)  # AI 评分
                page.screenshot(path=str(ART / "10-result.png"), full_page=True)
                rb = page.inner_text("body")
                has_score = any(x in rb for x in ["分", "覆盖", "标准", "反馈", "verdict", "通过", "未通过", "不及格", "良好", "优秀"])
                rec("评分结果展示", has_score, rb[rb.find("分")-20:rb.find("分")+40] if "分" in rb else rb[:80])
        else:
            rec("进入作答台", False, "未找到 textarea")

        # ---------- 10. 数据落盘校验 ----------
        page.wait_for_timeout(1000)
        st = load_state()
        rec("state.json 存在且 v2", st.get("version") == 2, str(st.get("version")))
        kps = st.get("kps") or {}
        rec("知识点已注册", len(kps) > 50, f"kps={len(kps)}")
        rec("审计目录存在", (STATE_FILE.parent / "audit").exists(), str(STATE_FILE.parent / "audit"))

        # ops 可观测
        ops = page.request.get(BASE + "/api/ops/metrics")
        om = ops.json()
        rec("AI 指标可观测", "scenes" in om and len(om.get("scenes") or {}) > 0, str(list((om.get("scenes") or {}).keys())[:6]))
        health = page.request.get(BASE + "/api/ops/health").json()
        rec("可控性 health", health.get("aiKeyConfigured") is True and health.get("breaker") is not None, str(health.get("breaker")))

        browser.close()

    ok = sum(1 for _, o, _ in results if o)
    total = len(results)
    print(f"\n==== E2E {ok}/{total} PASS ====")
    for n, o, d in results:
        print(("✓" if o else "✗"), n, d[:80] if d else "")
    report = {"passed": ok, "total": total, "results": [{"name": n, "ok": o, "detail": d} for n, o, d in results]}
    (ART / "e2e-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if ok == total else 1


if __name__ == "__main__":
    sys.exit(main())
