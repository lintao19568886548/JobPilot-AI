const labels = {
  // 通用状态
  ACTIVE: '启用', INACTIVE: '停用', ENABLED: '已启用', DISABLED: '已停用',
  READY: '就绪', PENDING: '待处理', PROCESSING: '处理中', IN_PROGRESS: '进行中',
  SUCCESS: '成功', SUCCEEDED: '成功', PARTIAL_SUCCESS: '部分成功', FAILED: '失败', DEAD: '终止',
  COMPLETED: '已完成', DONE: '已完成', CANCELLED: '已取消', CLOSED: '已关闭',
  BLOCKED: '已阻塞', EXPIRED: '已过期', ARCHIVED: '已归档', UNKNOWN: '未知',
  NEW: '新建', DRAFT: '草稿', PROPOSED: '待确认', PREPARED: '已准备', APPROVED: '已批准',
  REJECTED: '已拒绝', ACCEPTED: '已接受', DECLINED: '已谢绝', WITHDRAWN: '已撤回',
  CONFIRMED: '已确认', DISMISSED: '已忽略', RESOLVED: '已解决', SKIPPED: '已跳过',
  UNREAD: '未读', VIEWED: '已查看', FAVORITE: '已收藏', IGNORED: '已忽略',
  UNFAVORITE: '已取消收藏',
  DISCOVERED: '新发现', EVALUATED: '已评估', RECOMMENDED: '已推荐', QUEUED: '已入队',
  UNEVALUATED: '未评估', NEED_REVIEW: '需要审核', NEEDS_WORK: '需要完善', WAITING: '等待中',
  COMPLETE: '已完成', MISSING: '缺失', REVOKED: '已撤销', UP: '正常',
  true: '是', false: '否',
  externalMessagesSent: '已发送外部消息', externalSubmissions: '已提交外部申请',
  externalMutations: '已执行外部修改', userConfirmationRequired: '需要用户确认',
  automaticOfferDecisions: '自动作出 Offer 决策', destructiveDuplicateDeletes: '破坏性删除重复数据',

  // 求职与投递
  APPLIED: '已投递', REPLIED: '已回复', INTERVIEW: '面试中', OFFER: '已获 Offer',
  FULL_TIME: '全职', PART_TIME: '兼职', INTERNSHIP: '实习', OTHER: '其他',
  ONSITE: '现场办公', ONLINE: '线上', HYBRID: '混合办公', REMOTE: '远程',
  ACTIVELY_LOOKING: '积极求职', OPEN_TO_OPPORTUNITIES: '接受机会', NOT_LOOKING: '暂不求职',
  AI_RECOMMENDED: 'AI 推荐', MANUAL: '手动创建', MANUAL_ONLY: '仅手动',
  RECOMMEND: '推荐', CONSIDER: '建议考虑', NOT_RECOMMENDED: '不推荐', RECEIVED: '已收到',
  INBOUND: '对方联系', OUTBOUND: '主动联系', FOLLOW_UP: '跟进', THANK_YOU: '致谢',
  EMAIL: '邮件', PHONE: '电话', SMS: '短信', WECHAT: '微信', LIEPIN: '猎聘',

  // 面试
  SCHEDULED: '已安排', NO_SHOW: '未出席', PHONE_SCREEN: '电话初筛', HR_INTERVIEW: 'HR 面试',
  TECHNICAL: '技术面试', SYSTEM_DESIGN: '系统设计', PROJECT_DEEP_DIVE: '项目深挖',
  BEHAVIORAL: '行为面试', MANAGER: '主管面试', WRITTEN_TEST: '笔试',
  INTERVIEW_1: '一面', INTERVIEW_2: '二面', INTERVIEW_3: '三面', FINAL: '终面',
  EASY: '简单', MEDIUM: '中等', HARD: '困难', PREDICTED: '预测题', ACTUAL: '实际题',
  PLANNED: '待进行', HR: '人力面试', TECHNICAL_FOUNDATION: '技术基础', ANSWER_NOTE: '回答记录',
  NOTE: '记录', STRENGTH: '优势', GAP: '缺口', RISK: '风险', INFO: '提示', WARNING: '警告',
  HIGH: '高', LOW: '低', CRITICAL: '严重', BLOCKER: '阻断项',

  // Offer 与薪酬
  CONSIDERING: '考虑中', CASH: '现金', EQUITY: '股权', VARIABLE_BONUS: '浮动奖金',
  ANNUAL: '年度', MONTHLY: '月度', HOUSING: '住房', MEAL: '餐补', TRANSPORT: '交通',
  INSURANCE: '保险', BENEFITS: '福利', WORK_LIFE: '工作生活平衡', GROWTH: '成长空间',
  STABILITY: '稳定性', ROLE_RISK: '岗位风险', LEAVE: '假期', LEARNING: '学习成长',
  HEALTH: '健康保障', DECISION: '决策', LOCATION: '地点',

  // 简历、技能与匹配
  MASTER: '主简历', DEFAULT: '默认', CURRENT: '当前', VERSIONED: '已版本化',
  BASIC_INFO: '基本信息', SUMMARY: '个人总结', EDUCATION: '教育经历', SKILLS: '技能',
  EXPERIENCE: '工作经历', PROJECTS: '项目经历', CERTIFICATIONS: '证书', AWARDS: '奖项',
  OPEN_SOURCE: '开源经历', DOCUMENT: '文档', CUSTOM: '自定义',
  LANGUAGE: '编程语言', FRAMEWORK: '开发框架', DATABASE: '数据库', CACHE: '缓存',
  MESSAGE_QUEUE: '消息队列', DEVOPS: '开发运维', CLOUD: '云服务', FRONTEND: '前端',
  TOOL: '工具', SKILL: '技能', EMBEDDING: '向量语义', PROJECT: '项目',
  PREFERENCE: '求职偏好', COMPANY: '公司', MATCH_FAILED: '匹配失败',
  JOB: '岗位', JOB_DIRECTION: '岗位方向', SALARY_BAND: '薪资区间', RESUME_VARIANT: '简历版本',
  RESUME_VERSION: '简历版本', MATCH_SCORE_BUCKET: '匹配分数区间',
  SKIPPED_NOT_CONFIGURED: '未配置，已跳过', MUST_HAVE: '必须项', NICE_TO_HAVE: '加分项',
  RELATED: '相关项',
  BALANCED: '均衡', CONCISE: '精简', STRUCTURED: '结构化', PARTIAL: '部分', ATS: 'ATS 优化',
  ADD: '新增', UPDATE: '更新', REMOVE: '移除', REPLACE: '替换', REORDER: '调整顺序',
  ATS_KEYWORD: 'ATS 关键词', USED: '已使用', VERIFIED: '已验证',
  RULE: '规则模式', HYBRID_MODE: '混合模式', PASS: '通过', WARN: '警告',
  EVIDENCED: '已有证据', NO_EVIDENCE: '缺少证据',
  TRUTH_CHECK_PASSED: '事实校验通过', TRUTH_CHECK_FAILED: '事实校验失败',
  HIGH_SCHOOL: '高中', ASSOCIATE: '大专', BACHELOR: '本科', MASTER_DEGREE: '硕士', DOCTOR: '博士',

  // 自动化、运维与平台
  DAILY_JOB_DIGEST: '每日岗位摘要', ACCESS_KEY: '访问密钥', REFRESH_KEY: '刷新密钥',
  JOB_CAPTURE: '岗位采集', MATCH_READ: '读取匹配', QUEUE_WRITE: '写入队列',
  DRAFT_WRITE: '写入草稿', ASSIST_PREPARE: '准备辅助材料', ASSIST: '人工辅助',
  START: '开始', PREPARE: '准备', REVIEW: '审核', APPLICATION: '申请',
  PLATFORM: '平台', CITY: '城市', INDUSTRY: '行业', SALARY: '薪资',
  MATCH_LEVEL: '匹配等级', SOURCE: '来源', STATUS: '状态',
  CONFIGURED: '已配置', NOT_CONFIGURED: '未配置', DEFAULT_OFF: '默认关闭',
  ALLOWED: '允许', DENIED: '拒绝', SHADOW: '影子模式', LOCAL_SAFE: '本地安全执行',
  RULES_ONLY: '仅规则', RULES_ENGINE: '规则引擎', OPENAI_COMPATIBLE: 'OpenAI 兼容接口', NONE: '无',
  CREATED_BY_USER: '用户创建', CREATED_BY_AI: 'AI 创建', TAILORED: '定制生成',
  backend: '后端服务', mysql: 'MySQL 数据库', redis: 'Redis 缓存', milvus: 'Milvus 向量库',
  aiService: 'AI 服务',
  'ai-service': 'AI 服务', frontend: '前端服务',
  'Chrome Extension': '浏览器扩展', 'Microsoft Edge': '微软 Edge 浏览器', Chrome: 'Chrome 浏览器',
  EXTENSION: '浏览器扩展', DEMO_CSV: 'CSV 演示导入', DEMO_EXTENSION: '扩展演示导入',
  DEMO_WEB: '网页演示导入', 'API client': 'API 客户端', 'Web session': '网页会话',
  PLATFORM_ASC: '平台升序', COMPANY_ASC: '公司升序', CITY_ASC: '城市升序',
  MATCH_DESC: '匹配度从高到低', SALARY_DESC: '薪资从高到低', PUBLISH_DESC: '发布时间从近到远',
  USER: '用户', ACCOUNT: '账户', WORKSPACE: '工作区', SECURITY: '安全', SESSIONS: '会话',
  APPLICATION_FACT: '申请事实', INTERVIEW_FACT: '面试事实',
  APPLICATION_FACTS: '申请事实', INTERVIEW_FACTS: '面试事实',
  EXTERNAL_MEETING_ACTIONS: '外部会议操作', AUTOMATIC_SUBMISSION: '自动投递',
  PHASE_9: '阶段 9', PHASE_10: '阶段 10',
  MANUAL_IMPORT: '手动导入', URL_IMPORT: '网址导入', FILE_IMPORT: '文件导入',
  EXTENSION_CAPTURE: '扩展采集', GENERIC_VISIBLE: '通用可见页面', DEMO_FIXTURE: '演示页面',
  TARGET_CITY_MISSING: '缺少目标城市', TARGET_SALARY_MISSING: '缺少目标薪资',
  DAILY_JOB_REFRESH: '每日岗位刷新', REEVALUATE_STALE_MATCHES: '重评过期匹配',
  GENERATE_SUGGESTIONS: '生成建议', CREATE_IN_APP_REMINDERS: '创建站内提醒',
  Discovered: '已发现', Evaluated: '已评估', Applied: '已投递', Replied: '已回复',
  Interview: '进入面试', Offer: '获得 Offer', Accepted: '已接受'
}

export function displayText(value) {
  if (value === null || value === undefined || value === '') return '—'
  const raw = String(value)
  const direct = labels[raw] ?? labels[raw.replaceAll(' ', '_')]
  if (direct) return direct
  if (raw.includes(' · ')) {
    const parts = raw.split(' · ')
    const translated = parts.map((part) => labels[part] ?? labels[part.replaceAll(' ', '_')] ?? part)
    if (translated.some((part, index) => part !== parts[index])) return translated.join(' · ')
  }
  return raw
}

export function displayGeneratedText(value) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
    .replace(/^Missing (.+)$/i, '缺少 $1')
    .replace(/^No project evidence for (.+)$/i, '缺少 $1 的项目证据')
    .replace(/^(.+) matches the job requirement$/i, '$1 符合岗位要求')
    .replace(/^(.+) provides project evidence for (.+)$/i, '$1 为 $2 提供项目证据')
    .replace(/^Project technology or description contains the required skill$/i, '项目技术栈或描述包含岗位要求的技能')
    .replace(/^No project fact supports this required skill$/i, '没有项目事实支撑该岗位所需技能')
    .replace(/^Matching provider or evaluation failed safely$/i, '匹配服务或评估已安全失败')
    .replace(/^One or more match items failed safely$/i, '一个或多个匹配项已安全失败')
    .replace(/^Hard filter rejected this job; see rule evidence\.$/i, '硬性筛选未通过；请查看规则证据。')
    .replace(/^Overall score ([\d.]+) with hard filter PASS\. LLM analysis was skipped because no provider is configured; effective weights were re-normalized\.$/i, '综合分 $1，硬性筛选已通过。未配置 LLM 服务商，已跳过分析并重新归一化有效权重。')
    .replace(/^Overall score ([\d.]+) with hard filter REJECT\.$/i, '综合分 $1，硬性筛选未通过。')
    .replace(/^Score uses only known dimensions; unknown dimensions are excluded from the denominator\.$/i, '仅使用已知维度评分；未知维度不会计入分母。')
    .replace(/^Cash dimensions are compared in (.+)\.$/i, '现金维度统一使用 $1 比较。')
    .replace(/^Cash dimensions are unknown because offers use different currencies; no exchange rate was invented\.$/i, 'Offer 使用不同币种，现金维度按未知处理；系统不会虚构汇率。')
}

export function displayBoolean(value) {
  return value ? '是' : '否'
}

export function displayEnabled(value) {
  return value ? '已启用' : '已停用'
}

export function displayCount(value, unit = '项') {
  return `${Number(value || 0)} ${unit}`
}

export const displayLabels = Object.freeze(labels)
