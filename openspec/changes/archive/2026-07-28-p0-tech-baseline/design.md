## Context

复刻项目以 MyBatis-Plus 为 ORM（用户决策保留，不换回原生 MyBatis），但原项目用 21 个 Mapper XML 实现了大量自定义 SQL（向量检索、join、批量、聚合）。Plus 的 BaseMapper 覆盖简单 CRUD，但上述复杂查询需等价补回。同时复刻为兼容无 PG 环境引入了 H2 降级 + Java 端余弦召回，现需回归 pgvector。已接入远程 PostgreSQL 16 + pgvector + pg_trgm。

## Goals / Non-Goals

**Goals**
- 向量召回统一走 pgvector `<=>` HNSW 原生 SQL。
- Mapper 层补齐原项目全部自定义查询（向量/join/批量/聚合）。
- 删除 H2 数据源降级与 Java 端余弦降级。
- POM 依赖与原项目对齐（删 h2、补 postgis-jdbc/jackson-databind、poi 收敛）。

**Non-Goals**
- 不换回原生 MyBatis（保留 MyBatis-Plus）。
- 不改对外 API 契约（Controller 端点对齐属 P2）。
- 不动 AI 服务 Mock 降级逻辑（仅数据源回归 PG，DeepSeek/百炼 key 仍按配置生效）。
- 不新建 Service 层逻辑（属 P1）——本阶段只补 Mapper 自定义方法 + VectorSearchService 壳，Service 调用在 P1。

## Decisions

### D1: 自定义查询用 @Select/@Insert 注解而非 XML
- **选择**: 用 MyBatis-Plus 的 `@Select`/`@Insert`/`@Update`/`@Delete` 注解实现自定义方法，参数用 `@Param`。
- **理由**: 与复刻现有 MemoryEntryMapper 风格一致；避免引入 21 个 XML 文件；Plus 原生支持注解 SQL。
- **替代**: XML mapper（原项目方式）——拒绝，因复刻选 Plus 且现有代码用注解。
- **约束**: 向量 SQL 用 `?::vector` 显式类型转换；数组用 `PgArrayTypeHandler`。

### D2: 向量召回路径 = VectorSearchService → Mapper.searchByVector
- **选择**: 新建 `service/VectorSearchService`，`searchCandidates` 调 `DocumentChunkMapper.searchByVector`；`CandidateMatchingTool` 删除内联 Java cosine，改调该 Service。
- **理由**: 与原项目分层一致（Tool 不直连 Mapper）。
- **风险**: 本阶段 VectorSearchService 先建壳 + 向量召回实现，四阶段匹配的其余阶段在 P1 CandidateMatchService 移植时补全。

### D3: H2 降级彻底删除
- **选择**: 删除 pom 的 h2 依赖、DataSourceConfig 的 H2 Bean、CandidateMatchingTool 的 Java cosine 分支、EmbeddingService/RerankService 等的 H2 兼容注释。
- **理由**: 已有远程 PG，降级逻辑是复刻偏离，清单 §2 明确要求删除。
- **权衡**: 无 PG 环境将无法启动——可接受（用户已有远程 PG）。

### D4: PgArrayTypeHandler + FloatVectorTypeHandler 双处理器
- **选择**: 保留 FloatVectorTypeHandler（vector 类型），新增 PgArrayTypeHandler（BIGINT[]/TEXT[]）。
- **理由**: consolidation_task.entry_ids 等数组列需要；原项目用 FloatArrayTypeHandler+PgArrayTypeHandler，复刻用 pgvector 故 FloatVectorTypeHandler 处理 vector、PgArrayTypeHandler 处理数组。

## Risks / Trade-offs

- [Risk] 删除 H2 降级后本地无 PG 调试不便 → Mitigation: 远程 PG 已就绪；README 注明启动需 PG。
- [Risk] 注解 SQL 含中文/特殊字符在 Java 字符串转义 → Mitigation: SQL 用纯英文 + 参数绑定，不内联中文。
- [Risk] pgvector `<=>` 在 HNSW 索引未建时退化为顺序扫描 → Mitigation: schema.sql 已建 HNSW 索引，远程 PG 已 CREATE EXTENSION vector。
- [Risk] PgArrayTypeHandler 与 Spring DB 类型不匹配 → Mitigation: 用 `connection.createArrayOf("bigint", array)` 显式指定 PG 类型。

## Migration Plan

1. POM 改动（删 h2、补 postgis-jdbc/jackson-databind、poi 收敛）→ `mvn compile` 验证。
2. 新增 PgArrayTypeHandler + 核对 entity typeHandler 标注。
3. Mapper 增补自定义方法（注解 SQL）。
4. 新建 VectorSearchService 壳 + searchCandidates 实现。
5. CandidateMatchingTool 删除 Java cosine 降级、改调 VectorSearchService（四阶段匹配的其余逻辑保留，P1 时拆入 CandidateMatchService）。
6. DataSourceConfig 删 H2 分支。
7. `mvn clean compile` + 启动连远程 PG 验证向量召回可用。
8. 回滚: git revert 本变更提交即可（无破坏性数据迁移）。

## Open Questions

- CandidateMatchingTool 本阶段是否完全改调 VectorSearchService，还是保留 Java cosine 作为 Mock 模式（mock.enabled=true）下的兜底？——决策：**完全删除**，与原项目一致；Mock 仅 AI 服务层保留。
