# schema-memory-rag Specification

## Purpose
TBD - created by archiving change memory-rag-alignment. Update Purpose after archive.
## Requirements
### Requirement: memory_entry category 默认值
schema.sql `memory_entry.category` SHALL `VARCHAR(50) DEFAULT 'general'`；tags 列保留但巩固不写入（行为对齐参考，参考无 tags 列）。

#### Scenario: category 默认
- **WHEN** 插入 memory_entry 未指定 category
- **THEN** 默认 'general'

### Requirement: memory_graph id 主键 + UNIQUE
schema.sql `memory_graph` SHALL 加 `id BIGSERIAL PRIMARY KEY` + `created_at` + `UNIQUE(source_entry_id, target_entry_id, relation_type)`（支持 `ON CONFLICT DO NOTHING` 边去重）。

#### Scenario: ON CONFLICT 去重
- **WHEN** 插入重复边
- **THEN** ON CONFLICT DO NOTHING，无 selectCount

### Requirement: document_chunk UNIQUE + created_at
schema.sql `document_chunk` SHALL 加 `UNIQUE(parent_type, parent_id, chunk_index)` + `created_at` + `chunk_type NOT NULL`。

#### Scenario: 分块去重
- **WHEN** 重复分块插入
- **THEN** UNIQUE 约束阻止重复

### Requirement: consolidation_task completed_at
schema.sql `consolidation_task` SHALL 加 `completed_at TIMESTAMP`（保留 updated_at 兼容）；entry_ids 类型 BIGINT[]。

#### Scenario: 完成时间戳
- **WHEN** 巩固完成
- **THEN** completed_at = NOW() 写入

