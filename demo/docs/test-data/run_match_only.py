#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""只重跑匹配 (岗位+简历已分析入库)，用更长超时。用法: python run_match_only.py [job_id]"""
import json, sys, io
import run_eval as E

# 强制 stdout 用 utf-8, 避免中文 mojibake
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", line_buffering=True)

JOB_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 2
TIMEOUT = 300


def req_long(method, path, token, **kw):
    url = E.BASE + path
    headers = kw.pop("headers", {})
    if token:
        headers["satoken"] = token
    import requests
    r = requests.request(method, url, headers=headers, timeout=TIMEOUT, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"_raw": r.text}


def main():
    token = E.login()
    E.log("logged in (hr)")
    E.log(f"running match for job {JOB_ID} (timeout={TIMEOUT}s) ...")
    _, j = req_long("POST", f"/api/matches/job/{JOB_ID}/run", token)
    here = E.HERE
    with open(__import__("os").path.join(here, "result.json"), "w", encoding="utf-8") as f:
        json.dump(j, f, ensure_ascii=False, indent=2)
    mdata = j.get("data", j)
    cands = mdata.get("candidates", [])
    E.log("recall_count=", mdata.get("recall_count"), "candidate_count=", mdata.get("candidate_count"))
    E.log("position_filters=", mdata.get("position_filters"))

    print("\n=== 候选人排序结果 ===")
    print(f"{'rank':<5}{'name':<8}{'overall':<9}{'skill':<7}{'exp':<7}{'proj':<7}{'soft':<7}{'vec':<7}{'rerank':<8}{'tier':<22}{'iv':<6}")
    for c in cands:
        print(f"{c.get('rank',''):<5}{c.get('name',''):<8}{c.get('overall_score',0):<9}{c.get('skill_score',0):<7}{c.get('experience_score',0):<7}{c.get('project_score',0):<7}{c.get('soft_score',0):<7}{c.get('vector_score',0):<7}{c.get('rerank_score',0):<8}{str(c.get('decision_tier','')):<22}{str(c.get('interview_id') is not None):<6}")

    m = E.metrics(cands)
    print("\n=== 量化指标 ===")
    print(json.dumps(m, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
