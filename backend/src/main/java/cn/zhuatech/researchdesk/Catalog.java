// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统目录、安全账号与有界配置，保护初始管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Catalog {
  final Store db;
  final Access access;
  final BCryptPasswordEncoder encoder;
  final WorkbookIO workbook;

  public Catalog(Store db, Access access, BCryptPasswordEncoder encoder, WorkbookIO workbook) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
    this.workbook = workbook;
  }

  /** 目录读取仅允许全部范围系统管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String resource) {
    access.admin();
    return switch (resource) {
      case "accounts" -> db.all(Account.class);
      case "roles" -> db.all(Role.class);
      case "departments" -> db.all(Department.class);
      case "dictionary" -> db.all(Subject.class);
      case "menus" -> db.all(Menu.class);
      case "settings" -> db.all(Setting.class);
      case "mappings" -> db.all(ImportMapping.class);
      case "permissions" ->
          Bootstrap.PERMISSIONS.entrySet().stream()
              .map(e -> Map.of("code", e.getKey(), "label", e.getValue()))
              .toList();
      default -> throw new Problem(404, "目录不存在");
    };
  }

  /** 明确字段更新，稳定代码与受引用的归属不能随意改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String resource, Long id, Map<String, Object> b) {
    db.writeLock();
    access.admin();
    Object result;
    switch (resource) {
      case "accounts" -> {
        Account a = id == null ? new Account() : db.get(Account.class, id);
        if (id != null) {
          Rules.version(a, b.get("version"));
          Rules.check(id != 1, "初始管理员通过本人入口修改密码");
        }
        String username = Rules.text(b.get("username"), 60).toLowerCase(Locale.ROOT);
        Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "登录名格式不正确");
        if (id != null) Rules.check(username.equals(a.username), "登录名不可变更");
        Long dept = Rules.id(b.get("departmentId")), role = Rules.id(b.get("roleId"));
        Rules.check(db.get(Department.class, dept).enabled, "部门已停用");
        db.get(Role.class, role);
        if (id != null && !Objects.equals(a.departmentId, dept))
          Rules.check(
              db.query(Project.class, "from Project where ownerId=?1 or creatorId=?1", id).isEmpty()
                  && db.query(Member.class, "from Member where accountId=?1", id).isEmpty(),
              "已参与项目的账号不能迁移部门");
        String password = Rules.optional(b.get("password"), 128);
        if (id == null || !password.isEmpty()) {
          Rules.password(password);
          a.passwordHash = encoder.encode(password);
        }
        a.username = username;
        a.displayName = Rules.text(b.get("displayName"), 120);
        a.departmentId = dept;
        a.roleId = role;
        a.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(a);
        result = a;
      }
      case "roles" -> {
        var r = id == null ? new Role() : db.get(Role.class, id);
        if (id != null) {
          Rules.version(r, b.get("version"));
          Rules.check(id != 1, "初始管理员角色受保护");
        }
        String scope = Rules.text(b.get("scope"), 20);
        Rules.check(Set.of("ALL", "DEPARTMENT", "ASSIGNED").contains(scope), "范围无效");
        if (id != null) Rules.check(r.scope.equals(scope), "已建立角色的范围不可改写，请新增角色");
        Rules.check(b.get("permissions") instanceof List<?>, "请选择权限");
        var perms = new TreeSet<String>();
        for (var v : (List<?>) b.get("permissions")) {
          String code = Rules.text(v, 60);
          Rules.check(Bootstrap.PERMISSIONS.containsKey(code), "未知权限");
          perms.add(code);
        }
        Rules.check(!perms.contains("admin") || scope.equals("ALL"), "系统管理权限须全部范围");
        Rules.check(
            perms.stream().allMatch(p -> p.equals("admin")) || perms.contains("projects"),
            "业务权限须同时选择项目查看");
        Rules.check(
            !scope.equals("ASSIGNED")
                || (!perms.contains("finance.write") && !perms.contains("finance.review")),
            "财务接入须部门或全部范围");
        r.name = Rules.text(b.get("name"), 120);
        r.scope = scope;
        r.permissions = String.join(",", perms);
        if (id == null) db.save(r);
        result = r;
      }
      case "departments" -> {
        var d = id == null ? new Department() : db.get(Department.class, id);
        if (id != null) {
          Rules.version(d, b.get("version"));
          Rules.check(id != 1, "初始管理部门受保护");
          Rules.check(d.code.equals(Rules.code(b.get("code"))), "部门代码不可修改");
        }
        d.code = Rules.code(b.get("code"));
        d.name = Rules.text(b.get("name"), 120);
        d.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(d);
        result = d;
      }
      case "dictionary" -> {
        var c = id == null ? new Subject() : db.get(Subject.class, id);
        if (id != null) {
          Rules.version(c, b.get("version"));
          Rules.check(c.code.equals(Rules.code(b.get("code"))), "科目代码不可修改");
        }
        c.groupCode = "SUBJECT";
        c.code = Rules.code(b.get("code"));
        c.label = Rules.text(b.get("label"), 120);
        c.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(c);
        result = c;
      }
      case "menus" -> {
        Rules.check(id != null, "只维护已有业务菜单");
        var m = db.get(Menu.class, id);
        Rules.version(m, b.get("version"));
        m.label = Rules.text(b.get("label"), 120);
        m.position = Math.toIntExact(Rules.id(b.get("position")));
        Rules.check(m.position >= 0 && m.position <= 100, "顺序须0至100");
        m.enabled = Rules.flag(b.get("enabled"));
        result = m;
      }
      case "settings" -> {
        Rules.check(id != null, "仅修改已有参数");
        var s = db.get(Setting.class, id);
        Rules.version(s, b.get("version"));
        String value = Rules.text(b.get("parameterValue"), 160);
        switch (s.code) {
          case "warning_days" ->
              Rules.check(
                  Integer.parseInt(value) >= 1 && Integer.parseInt(value) <= 90, "提示天数须1至90");
          case "max_project_mb" ->
              Rules.check(
                  Integer.parseInt(value) >= 10 && Integer.parseInt(value) <= 100, "容量须10至100MB");
          case "app_name" -> Rules.text(value, 80);
          default -> throw new Problem(400, "参数不支持修改");
        }
        s.parameterValue = value;
        result = s;
      }
      case "mappings" -> {
        var m = id == null ? new ImportMapping() : db.get(ImportMapping.class, id);
        if (id != null) {
          Rules.version(m, b.get("version"));
          Rules.check(m.code.equals(Rules.code(b.get("code"))), "映射代码不可变更");
          Rules.check(m.departmentId.equals(Rules.id(b.get("departmentId"))), "映射部门不可变更");
        }
        m.departmentId = Rules.id(b.get("departmentId"));
        Rules.check(db.get(Department.class, m.departmentId).enabled, "部门已停用");
        m.code = Rules.code(b.get("code"));
        m.name = Rules.text(b.get("name"), 120);
        m.fieldsJson = Rules.text(b.get("fieldsJson"), 6000);
        workbook.mapping(m.fieldsJson);
        m.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(m);
        result = m;
      }
      default -> throw new Problem(404, "目录不存在");
    }
    db.flush();
    access.audit("CATALOG_SAVE", resource + ":" + ((Row) result).id, null, "");
    return result;
  }

  /** 仅删除未引用的新建角色/科目/映射/部门，账号用停用保留审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String resource, Long id, Object version) {
    db.writeLock();
    access.admin();
    Row r =
        switch (resource) {
          case "roles" -> db.get(Role.class, id);
          case "dictionary" -> db.get(Subject.class, id);
          case "departments" -> db.get(Department.class, id);
          case "mappings" -> db.get(ImportMapping.class, id);
          default -> throw new Problem(400, "此目录使用停用管理，不允许删除");
        };
    Rules.check(
        !((resource.equals("roles") || resource.equals("departments")) && id == 1), "初始目录受保护");
    Rules.version(r, version);
    access.audit("CATALOG_DELETE", resource + ":" + id, null, "");
    db.delete(r);
  }

  /** 业务表单只得到必要的启用引用；无账号密码、全局财务明细或不可见项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> references() {
    var a = access.current();
    boolean all = access.role().scope.equals("ALL");
    return Map.of(
        "departments",
        db.all(Department.class).stream()
            .filter(d -> d.enabled && (all || d.id.equals(a.departmentId)))
            .toList(),
        "people",
        db.all(Account.class).stream()
            .filter(u -> u.enabled && (all || u.departmentId.equals(a.departmentId)))
            .map(u -> Map.of("id", u.id, "name", u.displayName, "departmentId", u.departmentId))
            .toList(),
        "categories",
        db.all(Subject.class).stream().filter(c -> c.enabled).toList(),
        "projects",
        access.has("projects") ? access.projects() : List.of());
  }
}
