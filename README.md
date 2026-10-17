[中文](README.md) | [English](README.en.md)

# ResearchDesk · 知华科研项目经费与履约协同系统

**知华科技（上海如静知华信息科技有限公司）** · [官网](https://www.zhuatech.cn/)

Java 21、Spring Boot、Vue 3、MySQL 科研项目工作台，将合同资金、预算版本、实际到账、财务凭证分摊、成果验收和结题材料关联到同一项目。面向单机构、多部门的小规模部署。

**源码公开、非商业使用，仅限个人学习交流。未经上海如静知华信息科技有限公司授权不得商用。** 商业授权、私有部署和定制开发须另行取得书面授权，详见 [LICENSE](LICENSE)。

## 适用场景与角色

适用于科研院所、高校二级单位、企业研发中心的任务与经费协同。科研负责人编制项目、预算及成果；科研管理人员独立复核立项、预算、延期和成果；财务经办导入线下流水、登记到账与分摊，财务复核人员独立核实；管理员维护人员、部门、角色、科目、菜单、参数和导入表头。

用户端与管理端共用同源网页，以角色菜单和后端权限划分入口。数据范围支持全部、本部门、本人负责或参与项目。经办与复核必须为不同账号，管理员同样受此限制。

## 已实现功能

- 项目搜索、状态筛选、排序、分页，草稿增删改，立项送审、撤回、独立批准及项目成员管理。
- 多资金来源、合同金额与真实证明文件；按来源、科目编制预算，多版本调整与独立复核。
- 实际到账凭据登记及完整冲正；批准后计入净到账，禁止冲正后透支已支出和待审占用。
- UTF-8 CSV / XLSX 财务流水导入、可配置表头、文件摘要和凭证号／行号去重；整批事务校验，拒绝公式及多工作表。
- 支出分摊到同一项目的多个来源和科目；金额精确相等，同时受预算及实际到账额度限制。待审分摊预留额度，驳回释放占用。
- 追加式完整冲正，保留原始凭证及分摊；业务无关凭证说明原因并独立批准排除。
- 里程碑、真实 PDF / PNG / JPEG 文件、同名版本、冻结提交、独立验收、退回补充及重新编制；项目或任务日期变更独立审签。
- 结题阻断检查、结余安排、冻结快照、独立复核与含预算／变更／批准记录的 ZIP 材料包；重开申请不覆盖历史快照和原始文件。
- 当前范围报表、到期／逾期任务提示、资金 CSV 导出、项目历史和管理审计。金额以整数分存储，JSON 用字符串传输。
- 会话登录、CSRF、登录失败节流、BCrypt、账号／部门停用即时生效、修改密码撤销旧会话。

详细规则见 [业务规则](docs/business.md) 和 [操作手册](docs/operations.md)。文件导入不代表已接通财务、银行、OA 或资助平台。

## 当前运行截图

截图使用独立 TEST 验收数据，不包含真实科研、客户或财务资料；不是默认种子数据。

| 页面 | 功能 |
| --- | --- |
| [登录](docs/screenshots/login.jpg) | 机构账号与会话入口 |
| [项目总览](docs/screenshots/overview.jpg) | 当前权限范围内资金及任务 |
| [项目预算](docs/screenshots/budgets.jpg) | 来源、科目和预算版本 |
| [财务核对](docs/screenshots/finance.jpg) | 原始流水与核对状态 |
| [成果与任务](docs/screenshots/milestones.jpg) | 文件版本与验收 |
| [经费报表](docs/screenshots/reports.jpg) | 资金来源及占用余额 |
| [管理账号](docs/screenshots/accounts.jpg) | 后台账号、角色与范围 |
| [系统参数](docs/screenshots/settings.jpg) | 到期天数及容量配置 |

![项目总览](docs/screenshots/overview.jpg)
![项目预算版本](docs/screenshots/budgets.jpg)
![成果文件与验收](docs/screenshots/milestones.jpg)

## 技术架构

```text
用户／管理网页 Vue 3 → 同源 Nginx → Spring Boot 4 / Java 21
                                    ├─ Security 会话 + CSRF
                                    ├─ JPA 事务 + 版本检查 + 写协调锁
                                    └─ Flyway → MySQL 8.4（业务与私有文件）
```

后端 Spring Boot 4.0.7、Hibernate JPA、Flyway、Apache POI 5.5.1、PDFBox 3.0.8；前端 Vue 3.5.43、Vite 8.1.5。业务文件与元数据同库保存，使小规模备份保持一致。写事务使用实例协调行锁，不承诺大规模并发；细节见 [架构](docs/architecture.md)。

## 目录结构

```text
backend/  Java业务、REST、安全、JPA与测试
  src/main/resources/db/migration/V1__researchdesk.sql
frontend/ Vue页面、表单、精确金额工具与品牌LOGO
docs/     业务、架构、接口、操作、部署、安全与当前截图
scripts/  私有环境生成、HTTP验收、备份恢复、发布检查
.github/workflows/quality.yml  自动质量与全新数据库验收
compose.yaml / compose.storage.yaml / .env.example / LICENSE / NOTICE
```

## 环境要求

- Docker Engine / Docker Desktop，Docker Compose v2；可访问镜像源及 Maven / npm 依赖源。
- 独立源码开发：JDK **21**、Maven **3.9**、Node.js **24.19.0+**、npm、MySQL **8.4**；Python **3.11+** 用于初始化与验收。
- 默认仅本机访问 `127.0.0.1:8172`。数据库和后端没有宿主暴露端口。资源与容量须在目标环境实测。

## 安装启动与数据库初始化

```bash
git clone https://github.com/zhuatech-han/zhuatech-researchdesk.git
cd zhuatech-researchdesk
python3 scripts/init-env.py
docker compose -p researchdesk config --quiet
docker compose -p researchdesk up --build -d --wait --wait-timeout 900
```

访问 [本机工作台](http://127.0.0.1:8172/)，健康检查 [health](http://127.0.0.1:8172/actuator/health)。初始化登录名为 `.env` 的 `ADMIN_USERNAME`（默认 `admin`）；密码由脚本随机生成，读取本机私有 `.env` 的 `ADMIN_PASSWORD`，无公共默认密码。脚本拒绝覆盖已有文件。

全新数据库由 Flyway 自动执行 `V1__researchdesk.sql`，再初始化管理账号、六个角色、菜单、六个预算科目、标准表头和系统参数。**不生成任何科研项目、凭证或成果示例。** 重启不重设密码、不覆盖业务数据；不得用手动建表代替迁移，不改写已应用的迁移。

默认命名卷 `mysql-data` 保存业务及文件。普通停止使用 `docker compose -p researchdesk down`；`down -v` 会删除该实例数据，只用于明确可销毁的测试实例。

## 配置说明

| 环境变量 | 作用 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | 私有数据库管理口令 |
| `DATABASE_PASSWORD` | 应用数据库口令，与 MySQL 用户一致 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 仅首次空库初始化账号与强密码 |
| `WEB_PORT` / `BIND_ADDRESS` | 网页端口与绑定地址，默认 `8172` / `127.0.0.1` |
| `COOKIE_SECURE` | HTTPS部署设为 `true` |
| `RESEARCHDESK_MYSQL_DATA_DIR` | 仅可选存储覆盖使用的独立空绝对目录 |
| `DATABASE_URL` / `DATABASE_USER` | 可覆盖后端连接；默认容器内 MySQL，不需填写 |

端口冲突修改私有 `.env` 的 `WEB_PORT` 后重新启动。`.env.example` 仅列空配置名，真实口令不得入 Git。新口令须12至72字节，含大小写字母和数字。

后台可维护工作台名称、1至90天到期提示、单项目10至100MB容量；默认40MB。文件每个不超过3MB，每项目最多100个版本。导入文件不超过1MB、500行、30列；日期 `YYYY-MM-DD`，金额为人民币元、最多两位小数。映射须覆盖九个标准字段，见 [接口与导入](docs/api.md)。

## 测试方式

```bash
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 -m pip install -r scripts/requirements-quality.txt
python3 -m black --check scripts
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/release-check.py
docker compose -p researchdesk-test config --quiet
docker compose -p researchdesk-test up --build -d --wait --wait-timeout 900
python3 scripts/smoke.py --env-file .env --private-state /tmp/researchdesk-quality-state.json
docker compose -p researchdesk-test down -v
```

HTTP脚本只允许初始空业务库，创建明确 TEST 数据与独立测试账号。不要在实际业务环境运行。私有测试状态必须放仓库外的新路径。后端包含25项金额／文件解析／JPA业务／会话安全测试；前端2项精度边界测试；脚本6项备份恢复保护测试。最新实际验收见 [验证记录](docs/verification.md)。镜像 Maven 构建不跳过测试。

## 部署、备份与恢复

见 [部署手册](docs/deployment.md)，含慢速外置盘场景的可选独立数据目录。对外部署需受控域名、HTTPS、Secure Cookie、边界访问控制和目标环境验收。默认数据库连接要求TLS加密，未配置生产CA与身份校验；外部数据库应配置可信证书及 `VERIFY_IDENTITY`。不自带证书、邮件、短信或机构单点登录。

```bash
python3 scripts/backup.py --env-file .env --project researchdesk --output /安全目录/researchdesk-backup.sql
python3 scripts/restore.py --env-file /私有目录/restore.env --project researchdesk-restore-check --input /安全目录/researchdesk-backup.sql
```

备份文件仅保存在仓库外，权限600。恢复工具只允许空的独立 `researchdesk-restore-*` 实例，拒绝覆盖已有业务库。数据库备份包括私有文件原字节，须保护访问并定期实测恢复。

## 已知限制与未实现

- 单机构、人民币、单实例小规模使用；不含多租户、多币种、自动付款、报销、法定会计账、税务判定、伦理或临床研究管理。
- 仅文件导入；无财务／银行／OA／资助系统API集成，无在线支付，无电子签章或数字存证认证。
- 到期提示在站内总览显示，按截止日列示最早100条可见未验收任务；不含后台邮件、短信、微信通知或推送任务，也没有可填写密钥的通知配置。
- 仅完整冲正；部分纠错须先完整冲正，再导入正确新凭证。到账登记不移动资金，结余说明不自动退款。
- 里程碑历史轮次保留文件版本和审计动作，不提供独立历史验收轮次材料包；正式归档以已批准结题快照为准。
- 文件有格式解析与容量边界，非杀毒服务；下载附件应遵循机构安全制度。所有历史版本计入容量，不提供业务文件物理删除。
- 有界列表最多10000条；总览、汇总及写协调适合小规模，不承诺高并发和海量文件。菜单停用只影响入口，接口权限仍按角色核验。
- 未验证目标机构的真实数据、制度或生产容量，不宣称可直接投入生产；使用前完成机构验收并取得商业授权。

## 授权与第三方

[LICENSE](LICENSE) 为知华源码公开非商业许可证。仅限个人学习交流，未经上海如静知华信息科技有限公司授权不得商用，不是 MIT / Apache 免费商用项目。第三方框架与依赖保留各自许可，见 [NOTICE](NOTICE)。

## 联系知华科技

**知华科技（上海如静知华信息科技有限公司）** · [https://www.zhuatech.cn/](https://www.zhuatech.cn/)

**商业授权或深度定制开发请联系知华科技。** 服务包括需求咨询、私有部署、二次开发、数据迁移和系统集成。微信：`zhuatech`、`zhuatech2`。

| 微信 zhuatech | 微信 zhuatech2 |
| --- | --- |
| ![知华科技微信 zhuatech](docs/images/wechat-zhuatech.png) | ![知华科技微信 zhuatech2](docs/images/wechat-zhuatech2.png) |
