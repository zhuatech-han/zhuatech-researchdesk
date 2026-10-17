# ResearchDesk 架构

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

Vue 3 + 同源Nginx → Spring Boot 4 / Java21 / Security会话与CSRF → JPA / Flyway / MySQL8。文件二进制和元数据同库事务保存，保证小规模备份一致性；不依赖宿主私有目录或客户既有数据库。

所有写事务先锁单例数据库协调行，随后核对当前权限、项目、版本、状态和额度；单实例面向小规模组织，不承诺高并发。实体版本阻止旧页面覆盖。金额使用整数分和精确求和，HTTP传输十进制字符串；时间按Asia/Shanghai呈现、业务日期显式录入。

职责划分：Models持久化实体；Store受控JPA查询与锁；Access/Auth目录和会话；Catalog后台配置；Research项目/预算/到账/成果/结题；Finance凭证文件接入和分摊；Documents真实文件与材料包；WorkbookIO安全CSV/XLSX处理。所有列表、详情、下载、导出和统计均应用同一项目范围。

初始库仅包含管理账号、基础角色/菜单/科目/表头映射/参数。测试数据由独立验收脚本显式创建，不作为默认客户案例。私有环境文件、SQL备份和测试状态不入Git。
