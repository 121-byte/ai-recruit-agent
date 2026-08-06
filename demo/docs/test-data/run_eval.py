#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""端到端跑候选人匹配并计算量化指标。用法: python run_eval.py"""
import json, time, sys, math, os
import requests

BASE = "http://127.0.0.1:8888"
HERE = os.path.dirname(os.path.abspath(__file__))
RESUME_DIR = HERE  # resume-0*.txt 与本脚本同目录

# 金标排序与分级相关性 (hire 意愿): 张三>李四>王五>赵六≈钱七>孙八
GOLDEN_GRADED = {"张三": 3, "李四": 2, "王五": 1, "赵六": 0, "钱七": 0, "孙八": 0}
BINARY_RELEVANT = {"张三", "李四"}  # 应被 RECOMMEND+
RESUME_FILES = [
    ("resume-01-zhangsan.txt", "张三"),
    ("resume-02-lisi.txt", "李四"),
    ("resume-03-wangwu.txt", "王五"),
    ("resume-04-zhaoliu.txt", "赵六"),
    ("resume-05-qianqi.txt", "钱七"),
    ("resume-06-sunba.txt", "孙八"),
]


def log(*a):
    print("[eval]", *a, flush=True)


def req(method, path, token, **kw):
    url = BASE + path
    headers = kw.pop("headers", {})
    if token:
        headers["satoken"] = token
    r = requests.request(method, url, headers=headers, timeout=120, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"_raw": r.text}


def login():
    _, j = req("POST", "/api/auth/login", None,
               headers={"Content-Type": "application/json"},
               data=json.dumps({"username": "hr_user", "password": "123456"}))
    return j["data"]["token"]


def create_job(token):
    with open(os.path.join(HERE, "job-java-backend.md"), "r", encoding="utf-8") as f:
        jd = f.read()
    body = {
        "title": "Java后端工程师", "department": "技术中心", "level": "高级",
        "location": "北京", "salaryMin": 25, "salaryMax": 45,
        "experienceMin": 3, "experienceMax": 5, "education": "本科",
        "headcount": 2, "category": "技术", "jdText": jd,
    }
    _, j = req("POST", "/api/jobs", token,
               headers={"Content-Type": "application/json"},
               data=json.dumps(body))
    return j["data"]["id"]


def analyze_job(token, job_id):
    _, j = req("POST", f"/api/jobs/{job_id}/analyze", token)
    return j


def upload_resume(token, fname):
    path = os.path.join(RESUME_DIR, fname)
    with open(path, "rb") as f:
        _, j = req("POST", "/api/resumes/upload", token,
                   files={"file": (fname, f, "text/plain")})
    data = j.get("data", {})
    resume = data.get("resume", data)  # 兼容 {resume:{...}} 或直接平铺
    return resume["id"], resume.get("candidateName") or resume.get("name")


def analyze_resume(token, rid, timeout=180):
    _, j = req("POST", f"/api/resumes/{rid}/analyze", token)
    task_id = j.get("taskId") or j.get("data", {}).get("taskId")
    if not task_id:
        # 同步降级
        return "SYNC", j
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        _, st = req("GET", f"/api/tasks/{task_id}/status", token)
        last = st
        data = st.get("data", st)
        status = (data.get("status") or "").upper()
        if status in ("SUCCESS", "FAILED"):
            return status, st
        time.sleep(2)
    return "TIMEOUT", last


def run_match(token, job_id):
    _, j = req("POST", f"/api/matches/job/{job_id}/run", token)
    return j


def metrics(cands):
    # cands: list of {name, rank, ...} in returned order
    order = [c["name"] for c in cands]
    log("system order:", order)
    gold_order = ["张三", "李四", "王五", "赵六", "钱七", "孙八"]

    # MRR (binary)
    mrr = 0.0
    for i, name in enumerate(order, 1):
        if name in BINARY_RELEVANT:
            mrr = 1.0 / i
            break

    # Precision@k, Recall@2
    def p_at(k):
        top = order[:k]
        return sum(1 for n in top if n in BINARY_RELEVANT) / k
    recall2 = sum(1 for n in order[:2] if n in BINARY_RELEVANT) / len(BINARY_RELEVANT)

    # NDCG@6 (graded)
    def dcg(rels):
        return sum((2**r - 1) / math.log2(i + 2) for i, r in enumerate(rels))
    sys_rels = [GOLDEN_GRADED.get(n, 0) for n in order]
    ideal_rels = sorted(GOLDEN_GRADED.values(), reverse=True)[:len(sys_rels)]
    ndcg = dcg(sys_rels) / dcg(ideal_rels) if dcg(ideal_rels) > 0 else 0.0

    # 排序正确率: 系统序与金标序的 Spearman / Kendall tau 简化为「完全匹配前2」
    top2_match = set(order[:2]) == set(gold_order[:2])

    # tier correctness
    tier_correct = {}
    for c in cands:
        n, t = c["name"], c.get("decision_tier")
        if n in BINARY_RELEVANT:
            tier_correct[n] = t in ("STRONG_RECOMMEND", "RECOMMEND")
        elif n == "王五":
            tier_correct[n] = t in ("REVIEW", "WEAK")
        else:
            tier_correct[n] = t in ("WEAK", "REJECT")
    interview_correct = {}
    for c in cands:
        has_iv = c.get("interview_id") is not None
        if c["name"] in BINARY_RELEVANT:
            interview_correct[c["name"]] = has_iv
        else:
            interview_correct[c["name"]] = not has_iv

    return {
        "system_order": order,
        "MRR": round(mrr, 4),
        "P@1": round(p_at(1), 4),
        "P@2": round(p_at(2), 4),
        "P@3": round(p_at(3), 4),
        "Recall@2": round(recall2, 4),
        "NDCG@6": round(ndcg, 4),
        "top2_correct": top2_match,
        "tier_correct": tier_correct,
        "interview_correct": interview_correct,
        "tier_correct_rate": round(sum(1 for v in tier_correct.values() if v) / len(tier_correct), 4),
        "interview_correct_rate": round(sum(1 for v in interview_correct.values() if v) / len(interview_correct), 4),
    }


def main():
    hr_token = login()
    log("logged in (hr)")
    # 建岗位需 OPS, 其余 HR 即可
    ops_login = req("POST", "/api/auth/login", None,
                    headers={"Content-Type": "application/json"},
                    data=json.dumps({"username": "ops_user", "password": "123456"}))
    ops_token = ops_login[1]["data"]["token"]
    log("logged in (ops)")

    job_id = create_job(ops_token)
    log("created job id=", job_id)

    token = hr_token  # 后续用 HR

    log("analyzing job ...")
    aj = analyze_job(token, job_id)
    aj_data = aj.get("data", aj)
    log("job analyze keys:", sorted([k for k in aj_data.keys()]) if isinstance(aj_data, dict) else aj_data)

    resumes = []
    for fname, expect_name in RESUME_FILES:
        rid, name = upload_resume(token, fname)
        resumes.append((rid, name))
        log(f"uploaded {fname} -> id={rid} name={name}")

    for rid, name in resumes:
        log(f"analyzing resume {rid} ({name}) ...")
        status, detail = analyze_resume(token, rid)
        log(f"  resume {rid} analyze -> {status}")

    log("running match ...")
    mj = run_match(token, job_id)
    with open(os.path.join(HERE, "result.json"), "w", encoding="utf-8") as f:
        json.dump(mj, f, ensure_ascii=False, indent=2)
    mdata = mj.get("data", mj)
    cands = mdata.get("candidates", [])
    log("recall_count=", mdata.get("recall_count"), "candidate_count=", mdata.get("candidate_count"))
    log("position_filters=", mdata.get("position_filters"))

    print("\n=== 候选人排序结果 ===")
    print(f"{'rank':<5}{'name':<8}{'overall':<9}{'skill':<7}{'exp':<7}{'proj':<7}{'soft':<7}{'vec':<7}{'rerank':<8}{'tier':<22}{'iv':<6}")
    for c in cands:
        print(f"{c.get('rank',''):<5}{c.get('name',''):<8}{c.get('overall_score',0):<9}{c.get('skill_score',0):<7}{c.get('experience_score',0):<7}{c.get('project_score',0):<7}{c.get('soft_score',0):<7}{c.get('vector_score',0):<7}{c.get('rerank_score',0):<8}{str(c.get('decision_tier','')):<22}{str(c.get('interview_id') is not None):<6}")

    m = metrics(cands)
    print("\n=== 量化指标 ===")
    print(json.dumps(m, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
