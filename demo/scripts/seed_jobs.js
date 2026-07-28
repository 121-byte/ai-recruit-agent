/**
 * 岗位种子数据脚本
 * 删除旧数据 → 插入一批符合新标准的真实岗位数据
 * 用法: node scripts/seed_jobs.js
 */
const { Client } = require('pg')

const client = new Client({
  host: process.env.PG_HOST || 'localhost',
  port: 25432,
  user: 'postgres',
  password: process.env.PG_PASSWORD || '',
  database: 'postgres',
})

const JOBS = [
  {
    title: '高级 Java 工程师',
    department: '研发中心',
    level: '高级',
    location: '上海',
    category: '技术',
    salary_min: 25, salary_max: 40,
    education: '本科',
    experience_min: 3, experience_max: 5,
    headcount: 3,
    status: 'active',
    jd_text: '负责核心业务系统的设计与开发，参与技术方案评审，指导初中级工程师。要求精通 Java、Spring Boot、微服务架构，熟悉分布式系统设计。',
  },
  {
    title: '前端架构师',
    department: '研发中心',
    level: '架构师',
    location: '北京',
    category: '技术',
    salary_min: 35, salary_max: 55,
    education: '本科',
    experience_min: 5, experience_max: 10,
    headcount: 1,
    status: 'active',
    jd_text: '负责前端技术架构规划与落地，建立组件库和工程化体系，推动性能优化和技术创新。要求深入掌握 Vue/React、TypeScript、构建工具链。',
  },
  {
    title: 'AI 算法工程师',
    department: 'AI 实验室',
    level: '高级',
    location: '深圳',
    category: '技术',
    salary_min: 30, salary_max: 50,
    education: '硕士',
    experience_min: 3, experience_max: 8,
    headcount: 2,
    status: 'active',
    jd_text: '从事 NLP/CV 模型研发与落地，优化大模型推理性能，参与 Agent 系统设计。要求扎实的机器学习基础，熟悉 PyTorch/TensorFlow，有 LLM 实战经验。',
  },
  {
    title: '产品经理（B 端）',
    department: '产品中心',
    level: '中级',
    location: '上海',
    category: '产品',
    salary_min: 20, salary_max: 35,
    education: '本科',
    experience_min: 3, experience_max: 7,
    headcount: 2,
    status: 'active',
    jd_text: '负责企业级 SaaS 产品规划与迭代，撰写 PRD，跟进研发全流程。要求有 B 端产品经验，具备良好的数据分析和用户调研能力。',
  },
  {
    title: 'UI/UX 设计师',
    department: '产品中心',
    level: '中级',
    location: '上海',
    category: '设计',
    salary_min: 18, salary_max: 30,
    education: '本科',
    experience_min: 2, experience_max: 5,
    headcount: 1,
    status: 'active',
    jd_text: '负责产品视觉设计和交互体验优化，输出高保真原型和设计规范。要求精通 Figma/Sketch，有设计系统搭建经验者优先。',
  },
  {
    title: 'DevOps 工程师',
    department: '基础设施部',
    level: '中级',
    location: '北京',
    category: '技术',
    salary_min: 22, salary_max: 38,
    education: '本科',
    experience_min: 3, experience_max: 6,
    headcount: 1,
    status: 'active',
    jd_text: '负责 CI/CD 流水线维护、Kubernetes 集群管理、监控告警体系搭建。要求熟悉 Docker/K8s、Jenkins、Prometheus/Grafana。',
  },
  {
    title: '测试开发工程师',
    department: '研发中心',
    level: '中级',
    location: '上海',
    category: '技术',
    salary_min: 18, salary_max: 30,
    education: '本科',
    experience_min: 2, experience_max: 5,
    headcount: 2,
    status: 'active',
    jd_text: '负责自动化测试框架建设，编写接口/UI 自动化用例，参与质量保障体系建设。要求熟悉 Python/Java、Selenium/Cypress。',
  },
  {
    title: '技术总监',
    department: '研发中心',
    level: '总监',
    location: '上海',
    category: '技术',
    salary_min: 50, salary_max: 80,
    education: '本科',
    experience_min: 8, experience_max: 15,
    headcount: 1,
    status: 'draft',
    jd_text: '负责技术团队管理、技术战略规划、架构决策。要求有 30 人以上团队管理经验，具备大型分布式系统设计能力。',
  },
  {
    title: '数据分析师',
    department: '运营部',
    level: '中级',
    location: '上海',
    category: '数据',
    salary_min: 15, salary_max: 25,
    education: '本科',
    experience_min: 1, experience_max: 3,
    headcount: 2,
    status: 'active',
    jd_text: '负责业务数据分析、报表搭建、AB 实验分析。要求精通 SQL、Tableau/Power BI，具备 Python 数据分析能力。',
  },
  {
    title: 'HRBP（招聘方向）',
    department: '人力资源部',
    level: '高级',
    location: '北京',
    category: '职能',
    salary_min: 20, salary_max: 30,
    education: '本科',
    experience_min: 3, experience_max: 8,
    headcount: 1,
    status: 'active',
    jd_text: '负责技术团队招聘全流程，人才 mapping，校招/社招项目运营。要求有互联网招聘经验，具备优秀的沟通和谈判能力。',
  },
  {
    title: 'Python 后端开发',
    department: 'AI 实验室',
    level: '中级',
    location: '远程',
    category: '技术',
    salary_min: 20, salary_max: 35,
    education: '本科',
    experience_min: 2, experience_max: 5,
    headcount: 2,
    status: 'active',
    jd_text: '负责 AI 服务后端开发，构建数据处理 Pipeline 和模型推理服务。要求熟悉 Python、FastAPI/Flask、PostgreSQL、消息队列。',
  },
  {
    title: '安全工程师',
    department: '基础设施部',
    level: '高级',
    location: '上海',
    category: '技术',
    salary_min: 28, salary_max: 45,
    education: '本科',
    experience_min: 3, experience_max: 8,
    headcount: 1,
    status: 'closed',
    jd_text: '负责安全体系建设、渗透测试、安全审计、应急响应。要求熟悉 Web 安全、云安全、SDL 流程，持有 CISSP/CISP 优先。',
  },
  {
    title: '市场运营经理',
    department: '市场部',
    level: '中级',
    location: '上海',
    category: '运营',
    salary_min: 15, salary_max: 25,
    education: '大专',
    experience_min: 2, experience_max: 5,
    headcount: 1,
    status: 'draft',
    jd_text: '负责产品市场推广策略制定、渠道拓展、活动策划与执行。要求有 B 端市场运营经验，具备优秀的文案和数据分析能力。',
  },
  {
    title: '商务拓展经理',
    department: '市场部',
    level: '中级',
    location: '深圳',
    category: '运营',
    salary_min: 15, salary_max: 28,
    education: '本科',
    experience_min: 2, experience_max: 5,
    headcount: 2,
    status: 'active',
    jd_text: '负责企业客户开发和维护，商务谈判和合同管理。要求有 SaaS 行业 BD 经验，具备良好的客户关系管理能力。',
  },
  {
    title: '全栈工程师',
    department: '研发中心',
    level: '高级',
    location: '上海',
    category: '技术',
    salary_min: 28, salary_max: 45,
    education: '本科',
    experience_min: 3, experience_max: 8,
    headcount: 2,
    status: 'active',
    jd_text: '负责产品全栈开发，从前端界面到后端服务的完整实现。要求同时具备 Vue/React 和 Java/Node.js 开发能力。',
  },
]

async function main() {
  await client.connect()
  console.log('✅ 已连接 PostgreSQL')

  // 开启事务
  await client.query('BEGIN')

  try {
    // 1. 删除关联表数据（含外键引用的表）
    console.log('📦 删除关联数据...')
    await client.query('DELETE FROM outreach')
    await client.query('DELETE FROM interview')
    await client.query('DELETE FROM candidate_match')
    console.log('   ✅ outreach, interview, candidate_match 已清空')

    // 2. 删除旧岗位数据
    const delResult = await client.query('DELETE FROM job_profile')
    console.log(`   ✅ job_profile 已清空 (删除 ${delResult.rowCount} 条)`)

    // 3. 插入新数据
    console.log('📦 插入新岗位数据...')
    let inserted = 0
    for (const job of JOBS) {
      const res = await client.query(
        `INSERT INTO job_profile
         (title, department, level, location, category,
          salary_min, salary_max, education, experience_min, experience_max,
          headcount, status, jd_text, created_at, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13, NOW(), NOW())
         RETURNING id`,
        [job.title, job.department, job.level, job.location, job.category,
         job.salary_min, job.salary_max, job.education, job.experience_min, job.experience_max,
         job.headcount, job.status, job.jd_text]
      )
      inserted++
      console.log(`   [${inserted}] ID=${res.rows[0].id} ${job.title} (${job.department})`)
    }

    // 4. 提交事务
    await client.query('COMMIT')
    console.log(`\n🎉 完成！成功插入 ${inserted} 条岗位数据`)
  } catch (err) {
    await client.query('ROLLBACK')
    console.error('❌ 出错，已回滚:', err.message)
    process.exit(1)
  } finally {
    await client.end()
  }
}

main()
