# ResearchDesk 接口与导入说明

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

同源 `/api`，Cookie会话。写请求先GET `/api/auth/csrf`，按响应 `header` 和 `token` 传请求头。JSON请求使用Content-Type application/json；文件使用multipart/form-data。错误为401未登录、403无权限／CSRF、404不存在／范围不可见、400参数或业务状态、409旧版本／约束、413文件／列表超限、429登录节流。业务错误响应含message，安全过滤器含code。

金额字段以 `*Cents` 标识整数分，使用JSON十进制字符串，禁止浮点。写现有记录需带当前 `version`；DELETE通过查询参数version。动作体 `{version,action,note}`，处理说明必填。详情返回最新版本，旧页面须刷新后重新审阅，不自动重试旧审批。

| 接口 | 用途 |
| --- | --- |
| GET auth/csrf, GET auth/me, POST auth/login/logout/password | 会话与密码 |
| GET references, GET reports | 范围内引用与统计 |
| GET/POST projects, GET/PUT/DELETE projects/{id} | 项目列表与详情／草稿 |
| POST projects/{id}/actions | submit/withdraw/approve/reject |
| POST projects/{id}/members | accountId、enabled |
| POST projects/{id}/funds, PUT/DELETE projects/{id}/funds/{fundId} | 合同来源 |
| POST projects/{id}/budgets | reason，创建新版 |
| POST budgets/{id}/lines, PUT budgets/{id}/lines/{lineId}, DELETE budget-lines/{id} | 来源／科目预算行 |
| POST budgets/{id}/actions | submit/withdraw/approve/reject |
| POST projects/{id}/receipts, POST receipts/{id}/actions | 到账／完整冲正与approve/reject |
| POST projects/{id}/milestones, PUT projects/{id}/milestones/{milestoneId}, DELETE milestones/{id} | 草稿任务定义 |
| POST milestones/{id}/actions | submit/accept/reject/revise |
| POST projects/{id}/amendments, POST amendments/{id}/actions | 日期变更与approve/reject |
| POST projects/{id}/closeouts, POST closeouts/{id}/actions | CLOSE/REOPEN申请与approve/reject |
| POST projects/{id}/files | file、可选milestoneId与note |
| GET files/{id}, POST files/{id}/retire | 私有下载／撤回；撤回需version |
| GET projects/{id}/export, GET closeouts/{id}/bundle | CSV／已批准结题ZIP |
| GET finance, POST finance/import | 财务批次与流水导入 |
| POST finance/entries/{id}/submit | ALLOCATE/REVERSE/EXCLUDE |
| POST finance/reconciliations/{id}/review | approve/reject |
| GET/POST admin/{resource}, PUT/DELETE admin/{resource}/{id}, GET audit | 后台目录与审计 |

后台resource为accounts/roles/departments/dictionary/menus/settings/mappings，permissions仅可读取。菜单和参数只改既有项；账号使用停用，不物理删除。角色权限为允许代码列表，范围ALL/DEPARTMENT/ASSIGNED；已有角色范围不改写。后台初始保护项不能删除或重设。

## 财务文件

UTF-8 CSV或单工作表XLSX，第一行表头，九个必需字段对应的表头不重复，可有额外字段但总列数不超过30。空可选值保留该列。标准映射：

| 字段 | 标准表头 | 值 |
| --- | --- | --- |
| voucher | 凭证号 | 规范化大写，最长120字符 |
| lineNumber | 行号 | 1至100000整数 |
| bookedOn | 日期 | YYYY-MM-DD，2000年至今天 |
| amount | 金额 | 人民币元，最多2位小数，非零 |
| projectCode | 项目编号 | 可空；非空时匹配可见项目 |
| categoryCode | 科目代码 | 可空，仅保留原始来源提示，不自动决定分摊 |
| narrative | 摘要 | 可空，最长600字符 |
| reverseVoucher | 原凭证号 | 正数须空；负数必填 |
| reverseLine | 原行号 | 正数须空；负数必填正整数 |

```csv
凭证号,行号,日期,金额,项目编号,科目代码,摘要,原凭证号,原行号
TEST-EXP-001,1,2026-01-01,100.29,TEST-RD-001,MATERIAL,TEST材料支出,,
TEST-REV-001,1,2026-01-02,-100.29,TEST-RD-001,MATERIAL,TEST完整冲正,TEST-EXP-001,1
```

示例仅说明格式，实际需匹配机构项目及原凭证。每批500条记录、1MB；文件摘要及凭证号/行号全实例唯一。已识别项目须与映射部门一致且为草稿或执行中；未识别流水由财务人员核对归属，不能跨部门分摊。

ALLOCATE体带version、projectId、reason与lines数组（fundId/categoryId/amountCents），同来源同科目只允许一行，最多30行。更改已识别项目时必须 `remapConfirmed=true`。REVERSE不接受自选分摊，服务端沿用原已批准分摊；EXCLUDE需reason，由另一人批准后保留原始凭证。

## 结题材料

ZIP含snapshot.json、其SHA256、approval.json批准账号与实际审计时间、预算版本／日期变更／各类财务与任务CSV及被合同／已批准到账／验收成果引用的冻结文件。钱款单位在快照中为分、以字符串保存；通用明细CSV保留字段名与原始整数分。项目资金表单独导出以元列示。旧ZIP仅以其快照和文件摘要生成，项目重开不会改变旧包。
