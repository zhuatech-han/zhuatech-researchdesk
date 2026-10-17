# ResearchDesk 部署与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权、私有部署和定制开发微信 zhuatech / zhuatech2。

## 本机隔离部署

使用 README 的 `init-env.py` 生成模式600的私有 `.env`，确认端口未占用，然后执行 `docker compose -p researchdesk up --build -d --wait --wait-timeout 900`。Docker构建执行 Java 格式检查及全部测试；前端执行格式、静态检查、测试和生产构建。初始化无业务示例。

命名卷保存MySQL和私有文件。不要挂载客户既有库，不要复用其他项目卷。更新前备份并测试恢复，阅读新增迁移；使用相同Compose项目名和原私有环境文件重建服务。Flyway负责迁移，JPA只验证结构。

## 对外运行

先取得商业授权，在目标机构完成权限、流程、真实数据、容量与恢复验收。通过受控HTTPS反向代理访问同源前端。设置 `COOKIE_SECURE=true`，域名、TLS证书和访问控制由部署方提供；避免公网直接暴露MySQL或后端。默认只监听本机，确需改变BIND_ADDRESS时应先配置边界访问控制。

默认JDBC `sslMode=REQUIRED`仅要求加密，不校验CA与服务身份。机构数据库应使用可信CA、受控主机名和 `sslMode=VERIFY_IDENTITY`，按连接器要求配置证书／信任库及私有 `DATABASE_URL`；严禁把生产连接密码写进Compose或源码。应用数据库账号仅访问专用库，根账号不用于应用。会话30分钟；账号不共用、离职即停用。

依赖镜像：MySQL8.4、Maven3.9/Temurin21、Node24.19.0-alpine、Nginx1.29-alpine。目标镜像仓库和依赖源需可用；上线时按机构制品策略锁定验证后的镜像摘要。没有默认邮件、短信、支付、微信、OA或财务API密钥。

## 备份

```bash
python3 scripts/backup.py --env-file .env --project researchdesk --output /安全目录/researchdesk-backup.sql
```

使用同一数据库事务快照导出业务、历史文件字节和迁移记录。文件要求新路径且在仓库外，模式600，拒绝覆盖。备份可能包含敏感资料与密码散列；限制读权限、按机构要求加密、隔离保存并设置保留期限。应用写事务保持数据库一致，备份不涉及外部文件目录。

## 空实例恢复演练

1. 准备新的私有 `restore.env`，填写独立口令、独立测试端口，如 `WEB_PORT=18172`。
2. `docker compose --env-file /私有目录/restore.env -p researchdesk-restore-check up -d mysql`，等待数据库健康；先不要启动后端，否则会自动建表。
3. 运行 `python3 scripts/restore.py --env-file /私有目录/restore.env --project researchdesk-restore-check --input /安全目录/researchdesk-backup.sql`。工具只接受指定前缀实例与空库；存在任何表立即拒绝。
4. `docker compose --env-file /私有目录/restore.env -p researchdesk-restore-check up -d --wait --wait-timeout 900`。核对迁移、账号、关键业务金额、文件SHA256与历史材料包摘要。恢复后的账号密码以备份为准，环境初始化口令不会覆盖已有账号。
5. 演练完成只清理该测试实例：`docker compose --env-file /私有目录/restore.env -p researchdesk-restore-check down -v`。不要执行全局prune或删除其他项目卷。

普通停机 `down` 保留卷。没有业务卷丢失恢复功能，必须从已验收备份恢复。系统不承诺零停机升级、数据库高可用或跨区域容灾。

## 可选独立数据目录

Docker虚拟磁盘位于慢速外置盘时，可显式使用全新独立SSD目录。先创建仅用于该实例的空绝对路径，在私有 `.env` 设置 `RESEARCHDESK_MYSQL_DATA_DIR=/独立路径/researchdesk-mysql`，再执行：

```bash
docker compose -f compose.yaml -f compose.storage.yaml -p researchdesk up --build -d --wait --wait-timeout 900
```

该覆盖只改变所选实例MySQL数据挂载，不迁移现有命名卷。已有业务必须先按备份恢复流程迁移到另一个空实例，不能直接切换后宣称数据已迁移。停止或销毁实例须继续带相同两个 `-f` 参数；绑定目录不会被 `down -v` 删除，测试目录仅在确认属于本次可销毁验收后由操作者清理。恢复演练应使用另一个独立目录与端口。
