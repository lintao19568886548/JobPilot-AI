# JobPilot AI Phase 15：全系统中文化执行提示词

你现在需要继续开发本地项目 `D:\JobPilot AI`，完成 Phase 15「全系统中文化」。目标用户是中国大陆个人求职者，默认界面语言必须为简体中文（`zh-CN`）。本阶段不是做视觉重构，也不增加业务功能，而是在不破坏现有 API、数据库和自动化测试的前提下，把所有用户可见界面统一为自然、专业、易懂的中文。

## 一、执行原则

1. 先读取现有架构、API、数据库、Roadmap、TASKS、CHANGELOG 以及 Web/Extension 源码。
2. 中文化范围包括 Web 前端、浏览器扩展、HTML 标题与描述、用户可见的空状态、按钮、提示、确认框、表单校验、状态和枚举显示。
3. 不修改 API 路径、JSON 字段名、数据库列名、Java 类名、内部枚举值、TraceId、日志关键字、版本号或自动化协议。
4. 品牌名和行业通用技术名可以保留英文，例如 JobPilot AI、Java、Spring Boot、MySQL、Redis、RAG、LLM、API、Token、GitHub；其余界面文案优先使用中文。
5. 后端返回的英文状态与枚举在前端通过统一映射显示为中文，未知值必须安全回退到原值，不能导致空白或报错。
6. 不翻译用户自己录入的内容、公司名、岗位名、代码、URL、模型名、Prompt 版本或历史事实数据。
7. 不以简单全局替换处理语义不同的单词；文案必须结合求职业务语境翻译。
8. 删除、账号停用、申请批准、自动化执行等高风险操作必须保留原有确认与安全边界。

## 二、Web 前端范围

至少覆盖以下页面与公共组件：

- 登录页、主导航、顶部栏、全局搜索；
- Dashboard、Setup Center、Candidate Profile、Resume Center；
- Job Center、AI Recommendation、Application Center；
- Interview Center、Offer Center、Analytics、Learning Center；
- Safe Automation、Operations、AI Studio、Browser Extension、Settings；
- Match Analysis 等复用组件；
- Element Plus 的组件语言设为 `zh-CN`，包括日期选择、分页、弹窗和无障碍默认文本。

页面标题和导航建议统一为：

- Dashboard → 工作台
- Setup Center → 初始化中心
- Job Center → 岗位中心
- AI Recommendation → AI 岗位推荐
- Application Center → 申请中心
- Interview Center → 面试中心
- Offer Center → Offer 中心
- Analytics → 数据分析
- Learning Center → 学习中心
- Safe Automation → 安全自动化
- Operations → 系统运营
- AI Studio → AI 工作室
- Browser Extension → 浏览器扩展
- Candidate Profile → 候选人档案
- Resume Center → 简历中心
- Settings → 设置

英文 Eyebrow、Section Kicker、状态名、维度名和表格列名也必须中文化。API 枚举建立集中映射，至少涵盖申请、队列、岗位、面试、Offer、自动化、AI 调用、数据质量、简历来源、匹配结果、就业类型和技能分类。

## 三、浏览器扩展范围

1. 扩展名称、描述、配对、提取岗位、保存岗位、匹配、沟通草稿、Assist 队列、安全边界和错误提示全部使用简体中文。
2. 保留 JobPilot AI、Assist、Token、HTTP(S) 等必要技术或品牌词。
3. Manifest 的默认语言和描述与界面一致。
4. 不扩大权限，不增加 Cookie、密码、验证码或隐藏字段读取能力，不增加自动发送或自动提交行为。

## 四、后端与数据边界

1. 优先在客户端翻译显示层；不要把数据库已有英文枚举值改成中文。
2. 后端业务错误若直接呈现给普通用户，应增加稳定的中文消息映射；内部异常、日志和开发诊断信息保持技术准确性。
3. 不修改已有用户数据，不改变 Resume Version 不可变性，不触发外部投递或 AI 自动生成。

## 五、质量要求

1. 页面不得出现无意义的中英混杂，例如英文导航配中文正文、`rows`、`Current`、`Unknown`、`penalty` 等直接暴露。
2. 中文文案保持短、清楚、专业，避免机器翻译腔。
3. 处理长中文标题、窄屏换行和按钮宽度，不能因翻译导致遮挡、溢出或布局错位。
4. 浏览器控制台不得出现 error 或 warning。
5. 所有空状态、加载状态、失败状态、成功提示和危险确认均有中文文案。

## 六、验证要求

必须真实执行并记录：

- `frontend: npm run build`
- `extension: npm test`
- `extension: npm run build`
- 受影响的后端测试；若后端未改业务代码，至少执行发布 HTTP Smoke
- 更新并重启本地 Docker 前端镜像
- 在已登录浏览器中逐页访问主要路由，扫描可见英文残留
- 检查桌面宽度和至少一个窄屏宽度
- 检查浏览器控制台 error/warning
- 确认用户现有候选人资料和简历数据未被修改

只有实际通过才写 PASS。若因历史用户内容、公司名、岗位名或技术名保留英文，不计为中文化失败；必须在报告中说明保留规则。

## 七、文档与交付

1. 更新 `CHANGELOG.md` 和 `TASKS.md`，说明中文化范围、兼容策略和验证结果。
2. 不提交 `.env`、Token、密码、构建产物或运行日志。
3. 不 Push，不自动创建 Git Commit。
4. 最终报告至少给出：中文化文件数、Web 构建结果、Extension 测试/构建结果、HTTP Smoke、浏览器验收路由数、控制台错误数、保留英文类别、未完成项和技术债务。

立即按以上要求执行，不停留在文档阶段。
