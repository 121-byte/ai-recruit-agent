package com.example.recruit.eval;

import com.example.recruit.memory.PostgresLongTermMemory;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆检索评估种子数据 (实验脚本, 非框架)。
 *
 * <p>给 memory_entry / memory_graph 灌入一批带真实百炼 embedding 的记忆, 供 {@link MemoryRetrievalEvalTest} 评估。
 * 复用 {@link PostgresLongTermMemory#store} 走真实写入路径 (embed(key+value) + TTL 预算)。
 *
 * <p>数据驱动: 记忆与边以列表声明, 便于扩充。幂等: 先删 hr:1/hr:2 旧数据再插, 重跑不重复。
 *
 * <p>运行: {@code mvn test -Dtest=MemorySeedTest} (须真实模式, 百炼 key + PG)。
 */
@SpringBootTest
class MemorySeedTest {

    @Autowired
    private PostgresLongTermMemory longTermMemory;
    @Autowired
    private MemoryEntryMapper memoryEntryMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String A1 = "hr:1";
    private static final String A2 = "hr:2";

    // ─────────────────────────── 记忆条目 (key, value, category, agent) ───────────────────────────
    private static final String[][] ENTRIES_HR1 = {
            {"candidate:zhangsan", "张三,Java后端工程师,5年经验,熟悉SpringBoot/MyBatis,精通MySQL分库分表/Redis/Kafka/SpringCloud,主导核心交易系统", "fact"},
            {"zhangsan:techstack", "张三技术栈:Java,SpringBoot,MyBatis,MySQL分库分表,Redis,Kafka,SpringCloud微服务", "fact"},
            {"zhangsan:experience", "张三工作经验5年,曾任职某支付公司高级Java工程师,负责清结算与高并发场景", "fact"},
            {"zhangsan:project", "张三主导核心交易系统设计:高并发清结算,日均交易千万级,采用分库分表+Kafka异步+Redis缓存", "fact"},
            {"zhangsan:education", "张三本科,计算机科学与技术,某985院校", "fact"},
            {"zhangsan:preference", "张三期望薪资30K,倾向技术成长,不接受996,偏好金融与交易方向", "preference"},
            {"interview:zhangsan", "张三面试已通过,已发offer,定级高级,薪资28K,下月入职", "note"},
            {"candidate:lisi", "李四,后端开发工程师,4年经验,SpringBoot/MySQL/Redis/RabbitMQ,负责电商订单系统", "fact"},
            {"lisi:techstack", "李四技术栈:Java,SpringBoot,MySQL,Redis,RabbitMQ,SpringCloud", "fact"},
            {"lisi:interview", "李四面试安排:本周三下午2点技术面,面试官王总监,需考察分布式与消息队列", "note"},
            {"lisi:project", "李四负责电商订单系统:秒杀场景下RabbitMQ削峰,Redis预扣库存防超卖", "fact"},
            {"lisi:availability", "李四可到岗时间:下月初,目前在职交接中", "note"},
            {"candidate:wangwu", "王五,Java开发工程师,3年经验,SpringBoot/MySQL/Redis,无消息队列与微服务经验", "fact"},
            {"wangwu:techstack", "王五技术栈:Java,SpringBoot,MySQL,Redis,基础较扎实但广度不足", "fact"},
            {"candidate:zhaoliu", "赵六,Python后端,4年,Django/FastAPI/MySQL/Redis/Kafka,清结算系统,跨方向", "fact"},
            {"candidate:qianqi", "钱七,偏前端,Vue/React+Java2年,前端工程师方向,后端基础薄弱", "fact"},
            {"recommendation:java-backend", "Java后端候选人推荐:张三(强推荐STRONG),李四(推荐RECOMMEND),王五(待定REVIEW),赵六跨方向(不推荐)", "note"},
            {"skill:java-backend-req", "Java后端岗位要求:5年+,SpringBoot/MySQL分库分表/Redis/Kafka/SpringCloud,交易系统经验优先,本科及以上", "fact"},
            {"note:java-talent-pool", "Java人才库现有张三李四王五,其中张三最适合交易系统岗位,李四次之适合电商", "note"},
            {"project:trade-system", "核心交易系统项目:清结算+账务+风控,技术栈Java/Kafka/分库分表/Redis,张三主导设计", "fact"},
            // ── 新增 (初级/架构师/电商项目/offer/分层) ──
            {"candidate:sunba", "孙八,Java1年,应届初级,SpringBoot基础/MySQL基础,专科学历,无项目经验", "fact"},
            {"sunba:techstack", "孙八技术栈:Java基础,SpringBoot入门,MySQL增删改查,在学Redis", "fact"},
            {"sunba:education", "孙八专科,某职业技术学院,计算机应用技术,应届毕业生", "fact"},
            {"candidate:zhouwei", "周伟,Java架构师,8年,SpringCloud/分布式/高并发,主导微服务治理平台", "fact"},
            {"zhouwei:project", "周伟主导微服务治理平台:服务注册发现+配置中心+限流熔断+分布式事务,QPS万级", "fact"},
            {"zhouwei:techstack", "周伟技术栈:Java,SpringCloud Alibaba,Nacos,Sentinel,Seata,分布式事务,Service Mesh", "fact"},
            {"project:ecommerce", "电商订单系统项目:秒杀+订单+库存,技术栈Java/RabbitMQ/Redis,李四参与开发", "fact"},
            {"recommendation:architect", "架构师候选人推荐:周伟(STRONG,8年微服务治理),张三(REVIEW,5年可培养方向)", "note"},
            {"skill:architect-req", "架构师岗位要求:7年+,SpringCloud/分布式事务/高并发/限流熔断,微服务治理经验,本科及以上", "fact"},
            {"offer:zhangsan", "张三offer详情:高级Java工程师,28K,15薪,下月15号入职,试用期3个月", "note"},
            {"note:talent-tier", "人才分层:架构级(周伟),高级(张三),中级(李四王五),初级(孙八),跨方向(赵六Python钱七前端)", "note"},
            {"wangwu:project", "王五参与内部管理系统:CRUD为主,SpringBoot+MyBatis+Thymeleaf,无高并发场景", "fact"},
            {"zhaoliu:project", "赵六清结算系统:Django+PostgreSQL,对接银行接口,日均百万流水,异步对账", "fact"},
            {"interview:lisi:feedback", "李四面评反馈:基础扎实,分布式与RabbitMQ理解到位,但高并发经验不足,定级中级偏上", "note"},
    };

    private static final String[][] ENTRIES_HR2 = {
            {"candidate:zhouji", "周九,金融系统开发背景,6年,Java/SpringCloud/Flink,银行清结算+反洗钱系统", "fact"},
            {"zhouji:project", "周九负责银行反洗钱系统:大额交易监测,规则引擎+流式计算,对接人行报送", "fact"},
            {"zhouji:techstack", "周九技术栈:Java,SpringCloud,Flink,规则引擎,Kafka,熟悉金融监管报送", "fact"},
            {"candidate:wushi", "吴十,金融背景,5年,Java/大数据风控,信贷审批系统,机器学习特征工程", "fact"},
            {"note:finance-talent", "金融方向候选人:周九(反洗钱/清结算),吴十(风控信贷),均5年以上", "note"},
            // ── 新增 (衍生品/风控细化/岗位需求) ──
            {"candidate:zhengshiyi", "郑十一,金融衍生品系统,7年,Java/Scala,期权定价与风控引擎", "fact"},
            {"zhengshiyi:project", "郑十一负责期权定价引擎:蒙特卡洛模拟,Scala/Spark,毫秒级 Greeks 计算,对接交易台", "fact"},
            {"zhengshiyi:techstack", "郑十一技术栈:Java,Scala,Spark,量化,期权定价,风险中性估值,Black-Scholes", "fact"},
            {"wushi:project", "吴十信贷审批系统:特征工程+风控模型,XGBoost+规则引擎,反欺诈与授信额度", "fact"},
            {"note:finance-roles", "金融方向岗位需求:反洗钱(周九),风控信贷(吴十),衍生品定价(郑十一),均要求5年+", "note"},
    };

    // ─────────────────────────── 图谱边 (srcKey, tgtKey, rel, weight, agent) ───────────────────────────
    private static final String[][] EDGES_HR1 = {
            {"candidate:zhangsan", "zhangsan:techstack", "related_to", "0.9"},
            {"candidate:zhangsan", "zhangsan:experience", "related_to", "0.9"},
            {"candidate:zhangsan", "zhangsan:project", "participated_in", "0.95"},
            {"candidate:zhangsan", "zhangsan:education", "related_to", "0.7"},
            {"candidate:zhangsan", "interview:zhangsan", "interview_result", "0.85"},
            {"candidate:zhangsan", "offer:zhangsan", "offer_issued", "0.9"},
            {"interview:zhangsan", "offer:zhangsan", "offer_issued", "0.9"},
            {"zhangsan:project", "project:trade-system", "participated_in", "0.95"},
            {"candidate:zhangsan", "project:trade-system", "participated_in", "0.9"},
            {"zhangsan:techstack", "recommendation:java-backend", "related_to", "0.8"},
            {"zhangsan:techstack", "skill:java-backend-req", "related_to", "0.85"},
            {"candidate:lisi", "lisi:techstack", "related_to", "0.9"},
            {"candidate:lisi", "lisi:interview", "scheduled_interview", "0.95"},
            {"candidate:lisi", "interview:lisi:feedback", "interview_result", "0.85"},
            {"lisi:interview", "interview:lisi:feedback", "interview_result", "0.85"},
            {"candidate:lisi", "lisi:project", "participated_in", "0.9"},
            {"lisi:project", "project:ecommerce", "participated_in", "0.95"},
            {"candidate:lisi", "project:ecommerce", "participated_in", "0.9"},
            {"lisi:techstack", "recommendation:java-backend", "related_to", "0.7"},
            {"candidate:wangwu", "wangwu:techstack", "related_to", "0.9"},
            {"candidate:wangwu", "wangwu:project", "participated_in", "0.9"},
            {"candidate:wangwu", "note:java-talent-pool", "related_to", "0.5"},
            {"note:java-talent-pool", "candidate:zhangsan", "related_to", "0.85"},
            {"note:java-talent-pool", "candidate:lisi", "related_to", "0.75"},
            {"note:java-talent-pool", "project:trade-system", "related_to", "0.7"},
            {"recommendation:java-backend", "skill:java-backend-req", "related_to", "0.9"},
            // 新增边
            {"candidate:sunba", "sunba:techstack", "related_to", "0.9"},
            {"candidate:sunba", "sunba:education", "related_to", "0.8"},
            {"candidate:zhouwei", "zhouwei:project", "participated_in", "0.95"},
            {"candidate:zhouwei", "zhouwei:techstack", "related_to", "0.9"},
            {"zhouwei:project", "recommendation:architect", "related_to", "0.85"},
            {"zhouwei:techstack", "skill:architect-req", "related_to", "0.85"},
            {"recommendation:architect", "skill:architect-req", "related_to", "0.9"},
            {"candidate:zhangsan", "offer:zhangsan", "offer_issued", "0.9"},
            {"note:talent-tier", "candidate:zhouwei", "related_to", "0.85"},
            {"note:talent-tier", "candidate:zhangsan", "related_to", "0.8"},
            {"note:talent-tier", "candidate:sunba", "related_to", "0.7"},
            {"note:talent-tier", "candidate:lisi", "related_to", "0.75"},
            {"candidate:zhaoliu", "zhaoliu:project", "participated_in", "0.9"},
            {"note:java-talent-pool", "candidate:wangwu", "related_to", "0.6"},
    };

    private static final String[][] EDGES_HR2 = {
            {"candidate:zhouji", "zhouji:project", "participated_in", "0.95"},
            {"candidate:zhouji", "zhouji:techstack", "related_to", "0.9"},
            {"note:finance-talent", "candidate:zhouji", "related_to", "0.85"},
            {"note:finance-talent", "candidate:wushi", "related_to", "0.8"},
            {"candidate:wushi", "wushi:project", "participated_in", "0.95"},
            {"candidate:zhengshiyi", "zhengshiyi:project", "participated_in", "0.95"},
            {"candidate:zhengshiyi", "zhengshiyi:techstack", "related_to", "0.9"},
            {"note:finance-roles", "candidate:zhouji", "related_to", "0.85"},
            {"note:finance-roles", "candidate:wushi", "related_to", "0.85"},
            {"note:finance-roles", "candidate:zhengshiyi", "related_to", "0.9"},
            {"note:finance-talent", "candidate:zhengshiyi", "related_to", "0.75"},
    };

    @Test
    void seed() {
        clean(A1);
        clean(A2);

        Map<String, Long> ids1 = storeAll(A1, ENTRIES_HR1);
        Map<String, Long> ids2 = storeAll(A2, ENTRIES_HR2);

        buildEdges(A1, EDGES_HR1, ids1);
        buildEdges(A2, EDGES_HR2, ids2);

        System.out.println("[seed] hr:1 entries=" + ids1.size() + ", hr:2 entries=" + ids2.size());
        System.out.println("[seed] done. 跑 MemoryRetrievalEvalTest 即可出报告。");
    }

    private void clean(String agentId) {
        jdbcTemplate.update("DELETE FROM memory_graph WHERE agent_id = ?", agentId);
        jdbcTemplate.update("DELETE FROM memory_entry WHERE agent_id = ?", agentId);
    }

    private Map<String, Long> storeAll(String agentId, String[][] entries) {
        for (String[] e : entries) {
            longTermMemory.store(agentId, e[0], e[1], e[2]);
            System.out.println("[seed] " + agentId + " " + e[0]);
        }
        Map<String, Long> ids = new HashMap<>();
        for (MemoryEntry me : memoryEntryMapper.findByAgentId(agentId)) {
            ids.put(me.getMemoryKey(), me.getId());
        }
        return ids;
    }

    private void buildEdges(String agentId, String[][] edges, Map<String, Long> ids) {
        for (String[] e : edges) {
            Long src = ids.get(e[0]), tgt = ids.get(e[1]);
            if (src == null || tgt == null) {
                System.out.println("[seed] WARN missing key: " + e[0] + " or " + e[1]);
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO memory_graph(source_entry_id, target_entry_id, agent_id, relation_type, weight) " +
                            "VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (source_entry_id, target_entry_id, relation_type, agent_id) " +
                            "DO UPDATE SET weight = EXCLUDED.weight",
                    src, tgt, agentId, e[2], Double.parseDouble(e[3]));
        }
    }
}
