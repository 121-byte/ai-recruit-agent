# AI 智能招聘系统（复刻）

> 严格按照 `项目技术.md` 复刻的 AI 智能招聘系统。重点复刻 **Agent 核心架构**，
> 并完整覆盖记忆系统、工具链路、中间件、SSE 事件、LLM 服务、控制器与前端。
>
> 复刻前先用 ctx7 + jar 反编译 + javap 核对了真实 **AgentScope Java 2.0.0** API，
> 确保代码对真实库可编译、可启动。

## 一、技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Java | 17（21 亦可编译） |
| 框架 | Spring Boot | 3.4.1 |
| 响应式 | Spring WebFlux | 3.4.1 |
| Agent 框架 | AgentScope（HarnessAgent / ReAct / 中间件） | 2.0.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | PostgreSQL 16 + pgvector 0.8.5 + pg_trgm | — |
| 缓存 | Redis (Lettuce) | — |
| 消息队列 | RabbitMQ | — |
| 认证 | Sa-Token | 1.39.0 |
| 前端 | Vue 3 + Ant Design Vue + Vite + Pinia | — |

## 二、工程结构

```
demo/
├── pom.xml                      # Spring Boot 3.4.1 + AgentScope(gitee 仓库)
├── src/main/java/com/example/recruit/   # 128 个 Java 文件
│   ├── agent/                   # 35 类（core/routing/context/middleware/event/tool/nudge）
│   ├── memory/                  # 8 类（短期/长期/混合检索/巩固/遗忘）
│   ├── llm/                     # 9 类（DeepSeek/Embedding/Rerank/JsonGuard/MockChatModel...）
│   ├── dal/                     # 46 类（entity 22 + mapper 22 + handler 2）
│   ├── config/                  # 9 类
│   ├── controller/              # 14 类（§14 全套 API）
│   ├── service/                 # 5 类 + task/ 4 类
│   └── security/                 # StpInterfaceImpl
├── src/main/resources/
│   ├── application.properties   # §13.1
│   ├── agentscope.properties    # §13.2
│   ├── logback-spring.xml
│   └── sql/{schema,schema_auth}.sql   # 18 张表
└── frontend/                    # Vue 3，29 文件（8 视图 + 12 组件 + composable + api + router + store）
```

## 三、Agent 核心架构（重点，§4）

| 类 | 文档章节 | 职责 |
|----|---------|------|
| `ConversationAgentService` | §4.1 | 编排中枢：消息→SSE，按意图分流（闲聊/单工具/复合/HITL/批量） |
| `IntentRouter` | §4.2 | 分层置信度 + 动态锚点自学习 + Top-2 LLM 验证三段意图识别 |
| `DynamicAnchorPool` | §4.3 | LRU 动态锚点池（200/类、语义去重 0.95、持久化） |
| `ContextAssembler` | §4.4 | 偏好 + 混合检索记忆注入 RuntimeContext |
| `RecruitmentAgentService` | §4.5 | ReAct（HarnessAgent，8 工具 + 护栏 + Reflexion） |
| `SupervisorAgentService` | §4.6 | Supervisor（4 个 Agent-as-Tool，maxIters=5） |
| `SpecialistAgentFactory` | §4.7 | 4 个专家 Agent 工厂 |
| `ReWooExecutor` | §4.8 | ReWOO 三阶段并行批量执行器（4 线程池） |

## 四、与文档的偏差（已用 javap 核实真实 API）

复刻对齐真实 AgentScope Java 2.0.0，对文档二手描述做了如下修正：

| 文档写法 | 真实 API | 说明 |
|---------|---------|------|
| `TextDeltaEvent.getText()` | `TextBlockDeltaEvent.getDelta()` | 事件类名 |
| `ToolCallEvent.getName()/getArgs()` | `ToolCallStartEvent.getToolCallName()/getToolCallId()` | 工具调用事件 |
| `ToolResultEvent.getResult()` | `ToolResultTextDeltaEvent.getDelta()` + `ToolResultEndEvent` | 结果流式 + 结束事件 |
| `agent.streamEvents()` 在 `Agent` 上 | 实为 `HarnessAgent.streamEvents(Msg):Flux<AgentEvent>` | streamEvents 是 HarnessAgent 方法 |
| `CustomEvent.getData()` | `CustomEvent.getValue()`（返回 Map） | 自定义事件取值 |

`HarnessAgent.builder().middleware()/.compaction()/.memory()/.maxRetries()/.fallbackModel()/.permissionContext()/.disableFilesystemTools()` 等**全部逐字真实存在**，文档描述准确。

## 五、Mock 降级（无密钥/无基础设施可启动）

`app.mock.enabled=true`（默认）时：
- **AI 服务**（DeepSeek / Embedding / Rerank / WebSearch）返回确定性桩数据，配置真实 key 后自动切换真实调用
- **HarnessAgent** 使用 `MockChatModel`（实现 AgentScope `Model` 接口），Agent 路径无 key 也能跑
- **数据源**回退 H2 内存库（PostgreSQL 模式），无 PG 可启动
- **RabbitMQ** 配置与消费者 `@ConditionalOnProperty` 关闭，无 MQ 可启动
- 记忆/向量检索的 pgvector 语法在 H2 下 try/catch 静默降级

切换生产模式：在 `application.properties` 设 `app.mock.enabled=false` 并填齐 `app.ai.api-key`、PG、Redis、RabbitMQ。

## 六、启动方式

### 后端
```bash
cd demo
mvn spring-boot:run          # 默认 Mock 模式，:8888
# 验证
curl http://localhost:8888/api/health   # {"status":"UP","mock":true}
```

### 前端
```bash
cd demo/frontend
npm install
npm run dev                  # :5173，代理 /api → :8888
```

### 数据库（生产模式）
```bash
psql -U lly -d airecruit -f demo/src/main/resources/sql/schema.sql
psql -U lly -d airecruit -f demo/src/main/resources/sql/schema_auth.sql
# 种子账号: hr_user / ops_user，密码 123456
```

## 七、验证结果

| 项 | 结果 |
|----|------|
| `mvn clean compile -DskipTests` | ✅ 通过（128 文件，0 错误） |
| `mvn spring-boot:run`（Mock） | ✅ `Started DemoApplication in 6.8s`，Tomcat :8888 |
| 无 API Key / PG / Redis / RabbitMQ | ✅ 全部降级，正常启动 |
| AgentScope 真实依赖解析 | ✅ gitee 仓库 `agentscope-harness:2.0.0` 等 |

## 八、后续可补强项（非阻塞）

- Rerank instruct 改中文 + 文档摘要补 rawText 前 500 字（§6.6 P0 优化）
- `experiments/intent-evaluation/` 与 `experiments/rag-evaluation/` 评估脚本
- `docker-compose-langfuse.yml` 编排
- 将 EventController 接入 `ProactivePushService` 的 Sink（当前为桩）
