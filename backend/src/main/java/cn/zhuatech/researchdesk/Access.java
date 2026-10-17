// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 每次请求重新读取有效账号、权限和项目归属。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class Access {
  final Store db;
  final Clock clock;

  public Access(Store db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** 密码变更、停用账号或部门立即撤销已有业务访问。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !a.isAuthenticated() || a.getName().equals("anonymousUser"))
      throw new Problem(401, "请先登录");
    var rows = db.query(Account.class, "from Account where username=?1", a.getName());
    if (rows.isEmpty()) throw new Problem(401, "登录已失效");
    var u = rows.getFirst();
    if (!u.enabled || !Objects.equals(a.getDetails(), u.passwordHash))
      throw new Problem(401, "登录已失效，请重新登录");
    if (!db.get(Department.class, u.departmentId).enabled) throw new Problem(403, "部门已停用");
    return u;
  }

  /** 读取当前身份的最新角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Role role() {
    return db.get(Role.class, current().roleId);
  }

  /** 检查最新角色是否包含指定权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean has(String permission) {
    return List.of(role().permissions.split(",")).contains(permission);
  }

  /** 要求指定业务权限，缺失返回403。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account require(String permission) {
    var a = current();
    if (!has(permission)) throw new Problem(403, "没有此操作权限");
    return a;
  }

  /** 系统管理同时要求全部数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void admin() {
    require("admin");
    if (!role().scope.equals("ALL")) throw new Problem(403, "系统管理须为全部范围");
  }

  /** 全部/部门/成员范围一致用于查询、写入、下载和汇总。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Project p) {
    var a = current();
    return switch (role().scope) {
      case "ALL" -> true;
      case "DEPARTMENT" -> p.departmentId.equals(a.departmentId);
      case "ASSIGNED" ->
          p.ownerId.equals(a.id)
              || !db.query(
                      Member.class,
                      "from Member where projectId=?1 and accountId=?2 and enabled=true",
                      p.id,
                      a.id)
                  .isEmpty();
      default -> false;
    };
  }

  /** 检查项目读取权限与当前数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project project(Long id) {
    require("projects");
    var p = db.get(Project.class, id);
    if (!visible(p)) throw new Problem(404, "项目不存在或不可访问");
    return p;
  }

  /** 核对部门可见范围及启用状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void department(Long id) {
    if (!role().scope.equals("ALL") && !current().departmentId.equals(id))
      throw new Problem(404, "部门不可访问");
    Rules.check(db.get(Department.class, id).enabled, "部门已停用");
  }

  /** 财务接入批次按部门范围隔离；本人项目权限不授予整批财务流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void financeDepartment(Long id) {
    if (role().scope.equals("ASSIGNED")
        || (!role().scope.equals("ALL") && !current().departmentId.equals(id)))
      throw new Problem(404, "财务记录不可访问");
  }

  /** 读取当前角色可见的项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Project> projects() {
    require("projects");
    return db.all(Project.class).stream().filter(this::visible).toList();
  }

  /** 添加不可覆写审计记录；不记录密码或文件二进制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void audit(String action, Object object, Long projectId, String note) {
    var u = current();
    var e = new Audit();
    e.actorId = u.id;
    e.departmentId = u.departmentId;
    e.projectId = projectId;
    e.action = action;
    e.objectRef = Objects.toString(object, "");
    e.note = Rules.optional(note, 1600);
    e.createdAt = clock.instant();
    db.save(e);
  }

  /** 仅返回安全账号、可见导航及非秘密系统参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> profile() {
    var u = current();
    var r = role();
    return Map.of(
        "account",
        u,
        "role",
        r,
        "permissions",
        r.permissions.isBlank() ? List.of() : List.of(r.permissions.split(",")),
        "menus",
        db.all(Menu.class).stream()
            .filter(m -> m.enabled && (m.permission.isBlank() || has(m.permission)))
            .sorted(Comparator.comparingInt(m -> m.position))
            .toList(),
        "settings",
        db.all(Setting.class));
  }
}
