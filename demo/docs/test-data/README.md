# 候选人匹配测试数据

本目录提供一组偏真实的测试数据，用于端到端验证当前候选人匹配流程（Hybrid Ranking v2 lite）。

## 当前匹配流程（v2-lite）

`POST /api/matches/job/{jobId}/run` → `CandidateMatchService.matchForJob`：

1. **Stage-1 召回**（`VectorSearchService.searchCandidatesByJob`，top30）
   - 优先 chunk↔chunk：岗位的 5 类语义分块向量（skills/work_exp/projects/education/basic_info）↔ 简历同名分块向量，按类型取最近块、求和排序。
   - 岗位未分块 → 回退全 JD 向量 ↔ 简历分块（`searchCandidates`）。
   - 方向预过滤：从岗位标题提取关键词（Java/后端/前端…）+ `parsedJson.category` + `positionInfo.category`，对简历 `parsed_json->>'intended_position'` 和 `raw_text` 做 OR 匹配（**注意是 OR、且字面匹配，比较松**）。
2. **Stage-2 rerank**（`RerankService.rerankWithScore`）
   - `pool.size() <= 5`（DIRECT_EVAL_LIMIT）→ **跳过 rerank**，按召回序给合成分 `100 - i*5`。
   - `pool.size() > 5` → 真实调百炼 rerank（无 key 时走字符重叠 mock），`topN=min(10, pool.size())`，取前 RERANK_TOP(10)。
3. **Stage-3 证据化 LLM 评分**（`llmAssess`，对 rerank 后前 10 名逐个评）
   - `vectorScore = normalize(cosine(job.embedding, resume.embedding))`（≤1 时 ×100，0-100 量纲）。
   - LLM `chatJson` 输出 skill/experience/project/soft（0-100）+ matchedPoints/gaps/risks/interviewQuestions/summary。失败降级为 `50 + vectorScore*0.3`。
4. **分数融合**：`final = skill×0.30 + experience×0.25 + project×0.20 + vector×0.10 + rerank×0.10 + soft×0.05`。
5. **决策分层** `decisionTier`：skill<50 且 exp<50 → WEAK；否则 ≥85 STRONG_RECOMMEND / ≥75 RECOMMEND / ≥60 REVIEW / ≥45 WEAK / <45 REJECT。
6. **落库 + 条件建面试**：仅 STRONG_RECOMMEND / RECOMMEND 自动建 Interview(pending)；其余不建。`match_details` 含完整 scoreBreakdown/retrieval/rerank/matchedPoints/gaps/risks/interviewQuestions/decision。

## 测试数据构成

- `job-java-backend.md` — 岗位 JD（用于创建岗位）。
- `resume-01-zhangsan.txt` ~ `resume-06-sunba.txt` — 6 份简历（上传后分析）。6 份是为触发 rerank（pool>5）。

6 份简历的预期画像（具体分数取决于 LLM，下表为趋势）：

| 文件 | 候选人 | 技术栈 | intended_position | 预期 tier | 建面试 |
| --- | --- | --- | --- | --- | --- |
| resume-01 | 张三 | Java5年/SpringBoot/MySQL分库分表/Redis/Kafka/SpringCloud/交易系统 | Java后端工程师 | STRONG_RECOMMEND | 是 |
| resume-02 | 李四 | Java4年/SpringBoot/MySQL/Redis/RabbitMQ/SpringCloud/电商 | 后端开发工程师 | RECOMMEND | 是 |
| resume-03 | 王五 | Java3年/SpringBoot/MySQL/Redis/无消息队列/无微服务 | Java开发工程师 | REVIEW | 否 |
| resume-04 | 赵六 | **Python**4年/Django/FastAPI/MySQL/Redis/Kafka/清结算（跨方向） | 后端开发工程师 | WEAK（skill 低） | 否 |
| resume-05 | 钱七 | Vue/React + Java2年/SpringBoot/MySQL基础（偏前端，靠 raw_text 含"Java"溜过预过滤） | 前端工程师 | WEAK | 否 |
| resume-06 | 孙八 | Java1年/SpringBoot基础/MySQL基础/专科（应届初级） | Java开发 | REJECT | 否 |

## 测试步骤（真实模式：PG+pgvector+Redis+RabbitMQ+DeepSeek+百炼 key）

> mock 模式下 LLM/Embedding 返回桩数据，匹配分数无意义，但可验证召回/rerank 链路不报错。要看到真实分层结果需用真实模式。

### 0. 登录拿 token（种子账号 hr_user / 123456）
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8888/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"hr_user","password":"123456"}' | jq -r '.data.token')
echo $TOKEN
```

### 1. 创建岗位（粘贴 job-java-backend.md 的 JD）
```bash
JOBID=$(curl -s -X POST http://127.0.0.1:8888/api/jobs \
  -H "satoken: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Java后端工程师","department":"技术中心","level":"高级","location":"北京",
       "salaryMin":25,"salaryMax":45,"experienceMin":3,"experienceMax":5,
       "education":"本科","headcount":2,"category":"技术",
       "jdText":"<把 job-java-backend.md 全文粘进来>"}' | jq -r '.data.id')
echo "JOBID=$JOBID"
```

### 2. 分析岗位（生成 parsedJson + 岗位分块 + embedding）
```bash
curl -s -X POST "http://127.0.0.1:8888/api/jobs/$JOBID/analyze" -H "satoken: $TOKEN" | jq
```
> 完成后 `job_profile.parsed_json` 非空、`document_chunk` 有 parent_type='job' 的 5 类分块。

### 3. 上传 6 份简历
```bash
for f in docs/test-data/resume-0*.txt; do
  curl -s -X POST http://127.0.0.1:8888/api/resumes/upload -H "satoken: $TOKEN" \
    -F "file=@$f" | jq '{uploaded, id: .data.id, name: .data.candidateName}'
done
```

### 4. 逐份分析简历（MQ 异步，每次返回 taskId；轮询到 SUCCESS）
```bash
# 对每个 resumeId 执行：
curl -s -X POST "http://127.0.0.1:8888/api/resumes/<resumeId>/analyze" -H "satoken: $TOKEN" | jq
# 轮询：
curl -s "http://127.0.0.1:8888/api/tasks/<taskId>/status" -H "satoken: $TOKEN" | jq
```
> 6 份都分析完，`document_chunk` 才有 parent_type='resume' 的分块，chunk↔chunk 召回才有数据。

### 5. 跑匹配
```bash
curl -s -X POST "http://127.0.0.1:8888/api/matches/job/$JOBID/run" -H "satoken: $TOKEN" | jq
```

### 6. 查看排序结果
```bash
curl -s "http://127.0.0.1:8888/api/matches/job/$JOBID" -H "satoken: $TOKEN" | jq
```

## 验证要点

- 召回数 `recall_count` 应接近 6（6 份都过预过滤，因预过滤是 OR 字面匹配）。
- `pool>5` → `rerank.applied=true`（真实 rerank 生效）；若只上传 ≤5 份则 `rerank.applied=false`、走合成分。
- `decision_tier` 分布应大致符合上表：张三/李四建了 interview（`interview_id` 非空），其余为空。
- `match_details.scoreBreakdown` 能看到六维分；`match_details.rerank` 能看到 rerank 是否应用及得分。
- 跨方向的赵六：`skillScore` 明显低（无 Java），即使 exp/project 因有后端经验尚可，final 仍偏低 → WEAK。
