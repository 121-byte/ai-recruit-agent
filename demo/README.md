# AI 智能招聘系统

一个面向招聘业务的 AI Agent 应用。项目以 Spring Boot、AgentScope 和 Vue 3 为基础，覆盖岗位、简历、候选人匹配、面试、外联与多轮 Agent 对话等流程。

## 项目亮点

### 多范式 Agent 编排

对话入口会先进行意图路由，再按任务形态选择执行方式：

- **闲聊**：直接使用流式 LLM 回复，避免进入工具调用链。
- **单一工具任务**：使用 ReAct Agent 执行岗位分析、简历搜索、候选人匹配、面试题生成等操作。
- **复合招聘任务**：使用 Supervisor Agent 调度岗位分析、匹配、面试和外联四个专家 Agent。
- **批量独立任务**：使用 ReWOO 进行规划、并行工具执行和结果汇总。
- **高风险操作**：进入 HITL 人工确认路径。
- **无法确定的请求**：安全回退为澄清提问，不进入工具链。

### 分层意图路由与调用成本控制

`IntentRouter` 以静态意图锚点的 Embedding 相似度作为第一层判断：

1. 高置信且候选类别有足够分差的低风险请求直接路由，不调用 LLM；
2. 中置信请求交给 LLM 在 Top-2 候选意图中二选一；
3. 低置信请求使用五类意图的完整 LLM 分类；
4. HITL 请求始终要求 LLM 确认，避免仅依赖向量匹配执行高风险动作。

路由过程会记录 LLM 实际返回的 token usage。当前使用的是**静态锚点路由**；动态锚点自学习和离线评估体系尚未接入生产链路。

### 会话记忆与混合检索

- Redis 保存短期会话历史，并支持历史压缩；
- PostgreSQL + pgvector 保存长期记忆；
- 对话结束后可自动提取有价值的长期记忆；
- 通过定时巩固、重要性衰减和容量控制管理记忆；
- `HybridMemoryRetriever` 组合向量检索、关键词检索、图谱关系扩展、RRF 融合、时间衰减、重要性加权和 rerank 精排。

### 候选人匹配链路

候选人匹配服务包含可解释的分阶段处理：

1. pgvector 召回候选人，并进行方向预过滤；
2. 候选池较小时进行条件性 rerank；
3. LLM 从多个维度生成匹配评分；
4. 将评分与规则权重组合为最终排序，并返回匹配理由。

### 面向 Agent 的安全与可观测性

- 输入护栏识别常见提示词注入和招聘偏见表达；
- 高风险工具使用 HITL 权限确认；
- Sa-Token 提供登录、会话和 RBAC 控制；
- SSE 输出层对手机号、身份证号和邮箱进行脱敏；
- Agent 事件映射为前端 SSE 事件，前端可展示执行状态、思考内容与最终回答；
- 关键 Agent 工具接入 Reflexion 中间件，对低质量结果注入反思提示并限制重试次数；
- 会话会记录消息、思考内容与每轮 token 用量，支持历史恢复、删除、重命名和导出。

## 功能概览

| 模块 | 已实现能力 |
| --- | --- |
| 对话 Agent | SSE 流式对话、Markdown 渲染、思考过程展示、会话管理、Token 统计、导出 |
| 岗位 | 岗位 CRUD、岗位分析 |
| 简历 | 上传与解析、简历 CRUD、语义搜索、链式分析 |
| 候选人匹配 | 候选人召回、匹配排序、匹配理由与反馈 |
| 面试 | 面试安排、状态流转、AI 面试题、流式面试对话与报告 |
| 外联 | 候选人触达内容生成与管理 |
| 认证与权限 | Sa-Token 登录登出、当前用户、用户管理、角色权限 |
| 异步任务 | RabbitMQ 分析任务与状态查询 |
| 仪表盘 | 招聘漏斗、活动与 Agent 相关统计 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3、Spring MVC / WebFlux、MyBatis-Plus |
| Agent | AgentScope Java、DeepSeek 兼容接口、ReAct、Supervisor、ReWOO |
| 数据与中间件 | PostgreSQL + pgvector、Redis、RabbitMQ |
| 检索 | Embedding、关键词检索、RRF、qwen rerank |
| 安全与观测 | Sa-Token、Actuator、Prometheus 指标、SSE、LangFuse 适配层 |
| 前端 | Vue 3、Vite、Ant Design Vue、Pinia、Vue Router、Marked + DOMPurify |

## 工程结构

```text
src/main/java/com/example/recruit
├── agent/                 # Agent 编排、路由、工具、SSE、护栏与追踪
├── common/                # 统一响应与全局异常处理
├── module/                # 按业务域组织的接口与应用服务
│   ├── identity/          # 登录、用户与权限
│   ├── job/               # 岗位
│   ├── resume/            # 简历与解析
│   ├── match/             # 候选人匹配
│   ├── interview/         # 面试与 AI 面试官
│   ├── outreach/          # 外联
│   ├── dashboard/         # 仪表盘
│   └── task/              # 异步任务
├── memory/                # 短期/长期记忆、巩固、遗忘与混合检索
├── infra/                 # LLM、Embedding、Rerank、文件解析与观测适配
├── dal/                   # 实体、Mapper、类型处理器
├── security/              # Sa-Token 权限映射
└── config/                # 应用与中间件配置

frontend/src
├── api/                   # HTTP 与 SSE 调用封装
├── components/            # 通用组件
├── composables/           # 流式对话等组合式逻辑
├── features/              # 按业务域组织的页面
└── router/                # 前端路由
```

## 核心类

| 类 | 职责 |
| --- | --- |
| `ConversationAgentService` | 对话编排、SSE 输出、意图分流、消息和 token 落库 |
| `IntentRouter` | 静态锚点 Embedding 路由、Top-2 与全量 LLM 分类 |
| `RecruitmentAgentService` | 单工具 ReAct Agent |
| `SupervisorAgentService` / `SpecialistAgentFactory` | Supervisor 与四个领域专家 Agent |
| `ReWooExecutor` | 批量独立任务的规划、并行执行与汇总 |
| `HybridMemoryRetriever` | 长期记忆的混合检索和 rerank |
| `CandidateMatchService` | 分阶段候选人匹配与排序 |
| `ConversationGuardrail` / `ReflexionMiddleware` | 输入护栏与工具结果反思 |

## 启动方式

### 前置条件

- JDK 17+
- Maven 3.9+
- Node.js 18+
- PostgreSQL（启用 pgvector）、Redis、RabbitMQ

在生产配置下，需在 `src/main/resources/application.properties` 中配置数据库、中间件和模型服务密钥。该文件仅用于本地环境，不应提交到仓库。

### 后端

```bash
cd demo
mvn clean spring-boot:run
```

默认地址：`http://127.0.0.1:8888`

健康检查：`http://127.0.0.1:8888/actuator/health`

### 前端

```bash
cd demo/frontend
npm install
npm run dev
```

默认地址：`http://127.0.0.1:5173`

## 当前边界

项目已具备上述业务与 Agent 运行能力，但以下内容仍作为后续演进项：

- 动态意图锚点的在线自学习与持久化接入；
- RAG、意图识别等模块的统一离线评估体系与指标看板；
- 基于 `SKILL.md` 的自定义招聘 Skill 加载机制；
- 完整的容器化部署与 OpenAPI 文档交付。
