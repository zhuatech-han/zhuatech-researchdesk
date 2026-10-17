// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库目录初始化；不创建科研、财务或成果演示业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final LinkedHashMap<String, String> PERMISSIONS = new LinkedHashMap<>();

  static {
    for (String pair :
        List.of(
            "projects:项目查看",
            "project.write:项目编制与成员",
            "project.review:项目立项与日期复核",
            "budget.write:预算编制",
            "budget.review:预算独立复核",
            "finance.write:到账与财务对账",
            "finance.review:财务独立复核",
            "delivery.write:成果编制与提交",
            "delivery.review:成果独立验收",
            "close.write:结题与重开申请",
            "close.review:结题独立复核",
            "reports:报表与导出",
            "admin:系统管理")) {
      String[] s = pair.split(":");
      PERMISSIONS.put(s[0], s[1]);
    }
  }

  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${ADMIN_USERNAME:admin}") String username,
      @Value("${ADMIN_PASSWORD}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 首次强口令和目录在同一事务初始化；重启不改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.writeLock();
    if (!db.all(Account.class).isEmpty()) return;
    Rules.password(password);
    var d = new Department();
    d.code = "HQ";
    d.name = "科研管理中心";
    db.save(d);
    role("系统管理员", "ALL", String.join(",", PERMISSIONS.keySet()));
    role(
        "科研负责人",
        "ASSIGNED",
        "projects,project.write,budget.write,delivery.write,close.write,reports");
    role(
        "科研管理复核",
        "DEPARTMENT",
        "projects,project.write,budget.review,project.review,delivery.review,close.review,reports");
    role("财务经办", "DEPARTMENT", "projects,finance.write,reports");
    role("财务复核", "DEPARTMENT", "projects,finance.review,reports");
    role("只读审阅", "ALL", "projects,reports");
    var a = new Account();
    a.username = Rules.text(username, 60).toLowerCase(Locale.ROOT);
    Rules.check(a.username.matches("[a-z0-9_.-]{3,60}"), "初始账号格式不正确");
    a.displayName = "系统管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = 1L;
    a.departmentId = d.id;
    db.save(a);
    String[][] menus = {
      {"overview", "项目总览", "projects"},
      {"projects", "科研项目", "projects"},
      {"finance", "财务接入与对账", "finance.write"},
      {"review", "财务复核", "finance.review"},
      {"reports", "经费报表", "reports"},
      {"admin", "系统管理", "admin"}
    };
    int i = 0;
    for (var row : menus) {
      var m = new Menu();
      m.code = row[0];
      m.label = row[1];
      m.permission = row[2];
      m.position = ++i;
      db.save(m);
    }
    for (String[] row :
        new String[][] {
          {"MATERIAL", "材料费"},
          {"TESTING", "测试化验加工费"},
          {"TRAVEL", "差旅会议费"},
          {"LABOUR", "劳务费"},
          {"EQUIPMENT", "设备费"},
          {"OTHER", "其他经批准支出"}
        }) {
      var c = new Subject();
      c.groupCode = "SUBJECT";
      c.code = row[0];
      c.label = row[1];
      db.save(c);
    }
    setting("app_name", "机构工作台名称", "知华 ResearchDesk");
    setting("warning_days", "到期提示天数", "14");
    setting("max_project_mb", "单项目文件容量MB", "40");
    var m = new ImportMapping();
    m.departmentId = d.id;
    m.code = "STANDARD";
    m.name = "标准财务流水";
    m.fieldsJson =
        "{\"voucher\":\"凭证号\",\"lineNumber\":\"行号\",\"bookedOn\":\"日期\",\"amount\":\"金额\",\"projectCode\":\"项目编号\",\"categoryCode\":\"科目代码\",\"narrative\":\"摘要\",\"reverseVoucher\":\"原凭证号\",\"reverseLine\":\"原行号\"}";
    db.save(m);
  }

  void role(String name, String scope, String permissions) {
    var r = new Role();
    r.name = name;
    r.scope = scope;
    r.permissions = permissions;
    db.save(r);
  }

  void setting(String code, String label, String value) {
    var s = new Setting();
    s.code = code;
    s.label = label;
    s.parameterValue = value;
    db.save(s);
  }
}
