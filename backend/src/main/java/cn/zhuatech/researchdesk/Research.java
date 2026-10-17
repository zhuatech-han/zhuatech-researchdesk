// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 科研任务、合同资金、预算、成果、日期变更及结题的持久状态机。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Research {
  final Store db;
  final Access access;
  final Accounting accounting;
  final Documents documents;
  final Clock clock;
  final ObjectMapper json;

  public Research(
      Store db,
      Access access,
      Accounting accounting,
      Documents documents,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.documents = documents;
    this.clock = clock;
    this.json = json;
  }

  /** 用户项目列表与搜索、排序、分页；不可见项目不计入总数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> list(String query, String state, String sort, int page, int size) {
    Rules.check(page >= 1 && size >= 1 && size <= 100, "分页无效");
    String q = Rules.optional(query, 120).toLowerCase(Locale.ROOT);
    var rows =
        access.projects().stream()
            .filter(
                p -> q.isBlank() || (p.code + " " + p.title).toLowerCase(Locale.ROOT).contains(q))
            .filter(p -> state.isBlank() || p.state.equals(state))
            .toList();
    Comparator<Project> order =
        switch (sort) {
          case "code" -> Comparator.comparing(p -> p.code);
          case "title" -> Comparator.comparing(p -> p.title);
          case "id" -> Comparator.comparing(p -> p.id);
          default -> throw new Problem(400, "排序字段无效");
        };
    var sorted = rows.stream().sorted(order.reversed()).toList();
    long offset = (long) (page - 1) * size;
    return Map.of(
        "items",
        sorted.stream().skip(offset).limit(size).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  void writable(Project p, String... states) {
    Rules.state(p.state, states);
  }

  void checkOwner(Long owner, Long department) {
    var a = db.get(Account.class, owner);
    Rules.check(a.enabled && a.departmentId.equals(department), "负责人须为项目部门的启用账号");
  }

  /** 新建或修改尚未送审项目；编号与负责人归属在激活后冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project saveProject(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("project.write");
    var p = id == null ? new Project() : access.project(id);
    if (id != null) {
      Rules.version(p, b.get("version"));
      writable(p, "DRAFT");
    }
    Long department = Rules.id(b.get("departmentId")), owner = Rules.id(b.get("ownerId"));
    access.department(department);
    checkOwner(owner, department);
    if (id != null)
      Rules.check(
          department.equals(p.departmentId)
              && owner.equals(p.ownerId)
              && Rules.code(b.get("code")).equals(p.code),
          "项目编号、归属部门和负责人不可改写");
    if (access.role().scope.equals("ASSIGNED")) Rules.check(owner.equals(u.id), "本人范围只能为自己建立项目");
    LocalDate start = Rules.date(b.get("startOn")), end = Rules.date(b.get("endOn"));
    Rules.check(end.isAfter(start) && !end.isAfter(start.plusYears(10)), "项目期间须大于0且不超过10年");
    p.code = Rules.code(b.get("code"));
    p.title = Rules.text(b.get("title"), 160);
    p.departmentId = department;
    p.ownerId = owner;
    p.startOn = start;
    p.endOn = end;
    p.note = Rules.optional(b.get("note"), 1600);
    if (id == null) {
      p.creatorId = u.id;
      p.state = "DRAFT";
      db.save(p);
    }
    access.audit("PROJECT_SAVE", p.id, p.id, "");
    db.flush();
    return p;
  }

  /** 空草稿允许删除，已有关联数据由外键保护；其余状态保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteProject(Long id, Object version) {
    db.writeLock();
    access.require("project.write");
    var p = access.project(id);
    Rules.version(p, version);
    writable(p, "DRAFT");
    Rules.check(
        db.query(Evidence.class, "from Evidence where projectId=?1", id).isEmpty(), "项目已有文件，不允许删除");
    access.audit("PROJECT_DELETE", id, id, "");
    db.delete(p);
  }

  /** 成员关系变更立即影响本人范围，负责人不可移除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void member(Long projectId, Map<String, Object> b) {
    db.writeLock();
    access.require("project.write");
    var p = access.project(projectId);
    writable(p, "DRAFT", "ACTIVE");
    Long uid = Rules.id(b.get("accountId"));
    var u = db.get(Account.class, uid);
    Rules.check(u.enabled, "账号已停用");
    if (!access.role().scope.equals("ALL"))
      Rules.check(u.departmentId.equals(p.departmentId), "仅全部范围可增加跨部门成员");
    Rules.check(!p.ownerId.equals(uid), "负责人不通过成员关系移除");
    var rows = db.query(Member.class, "from Member where projectId=?1 and accountId=?2", p.id, uid);
    var m = rows.isEmpty() ? new Member() : rows.getFirst();
    m.projectId = p.id;
    m.accountId = uid;
    m.enabled = Rules.flag(b.get("enabled"));
    if (m.id == null) db.save(m);
    access.audit("PROJECT_MEMBER", uid, p.id, m.enabled ? "加入" : "移除");
  }

  Evidence evidence(Long id, Long projectId) {
    var e = db.get(Evidence.class, id);
    Rules.check(e.projectId.equals(projectId) && !e.retired, "文件不属于项目或已撤回");
    return e;
  }

  /** 草稿阶段维护合同来源；每个来源的金额、引用和证明文件都实际保存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Fund saveFund(Long projectId, Long id, Map<String, Object> b) {
    db.writeLock();
    access.require("project.write");
    var p = access.project(projectId);
    writable(p, "DRAFT");
    var f = id == null ? new Fund() : db.get(Fund.class, id);
    if (id != null) {
      Rules.check(f.projectId.equals(p.id), "资金来源跨项目");
      Rules.version(f, b.get("version"));
    }
    f.projectId = p.id;
    f.code = Rules.code(b.get("code"));
    f.name = Rules.text(b.get("name"), 160);
    f.contractReference = Rules.text(b.get("contractReference"), 120);
    f.awardCents = Rules.cents(b.get("awardCents"), false);
    f.evidenceId = Rules.nullableId(b.get("evidenceId"));
    if (f.evidenceId != null) evidence(f.evidenceId, p.id);
    if (id == null) db.save(f);
    long total =
        db.query(Fund.class, "from Fund where projectId=?1", p.id).stream()
            .mapToLong(a -> a.awardCents)
            .reduce(0, Rules::add);
    Rules.check(total <= Rules.MAX, "项目合同资金总额超限");
    access.audit("FUND_SAVE", f.id, p.id, "");
    db.flush();
    return f;
  }

  /** 删除未引用的草稿资金来源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteFund(Long projectId, Long id, Object version) {
    db.writeLock();
    access.require("project.write");
    var p = access.project(projectId);
    writable(p, "DRAFT");
    var f = db.get(Fund.class, id);
    Rules.check(f.projectId.equals(p.id), "资金来源跨项目");
    Rules.version(f, version);
    db.delete(f);
    access.audit("FUND_DELETE", id, p.id, "");
  }

  /** 新版从当前有效预算复制；同项目只允许一个未决版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Budget createBudget(Long projectId, String reason) {
    db.writeLock();
    var u = access.require("budget.write");
    var p = access.project(projectId);
    writable(p, "DRAFT", "ACTIVE");
    Rules.check(
        db.query(
                Budget.class,
                "from Budget where projectId=?1 and state in ('DRAFT','PENDING')",
                p.id)
            .isEmpty(),
        "已有未完成预算版本");
    var b = new Budget();
    b.projectId = p.id;
    b.creatorId = u.id;
    b.reason = Rules.text(reason, 1600);
    b.state = "DRAFT";
    b.revisionNumber =
        db.query(Budget.class, "from Budget where projectId=?1", p.id).stream()
                .mapToInt(v -> v.revisionNumber)
                .max()
                .orElse(0)
            + 1;
    db.save(b);
    if (p.activeBudgetId != null)
      for (var old :
          db.query(
              BudgetLine.class,
              "from BudgetLine where budgetId=?1 order by id",
              p.activeBudgetId)) {
        var l = new BudgetLine();
        l.budgetId = b.id;
        l.fundId = old.fundId;
        l.categoryId = old.categoryId;
        l.amountCents = old.amountCents;
        db.save(l);
      }
    access.audit("BUDGET_CREATE", b.id, p.id, b.reason);
    return b;
  }

  /** 预算行显式校验来源、科目与版本；草稿删改不会改变生效额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BudgetLine saveBudgetLine(Long budgetId, Long id, Map<String, Object> b) {
    db.writeLock();
    access.require("budget.write");
    var budget = db.get(Budget.class, budgetId);
    var p = access.project(budget.projectId);
    writable(p, "DRAFT", "ACTIVE");
    Rules.state(budget.state, "DRAFT");
    var l = id == null ? new BudgetLine() : db.get(BudgetLine.class, id);
    if (id != null) {
      Rules.check(l.budgetId.equals(budgetId), "预算行版本不符");
      Rules.version(l, b.get("version"));
    }
    var f = db.get(Fund.class, Rules.id(b.get("fundId")));
    Rules.check(f.projectId.equals(p.id), "资金来源跨项目");
    var c = db.get(Subject.class, Rules.id(b.get("categoryId")));
    Rules.check(c.groupCode.equals("SUBJECT") && (c.enabled || id != null), "科目不可用");
    l.budgetId = budgetId;
    l.fundId = f.id;
    l.categoryId = c.id;
    l.amountCents = Rules.cents(b.get("amountCents"), false);
    if (id == null) db.save(l);
    access.audit("BUDGET_LINE_SAVE", l.id, p.id, "");
    db.flush();
    return l;
  }

  /** 删除预算草稿行，保持已生效版本不变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteBudgetLine(Long id, Object version) {
    db.writeLock();
    access.require("budget.write");
    var l = db.get(BudgetLine.class, id);
    var b = db.get(Budget.class, l.budgetId);
    var p = access.project(b.projectId);
    writable(p, "DRAFT", "ACTIVE");
    Rules.state(b.state, "DRAFT");
    Rules.version(l, version);
    db.delete(l);
    access.audit("BUDGET_LINE_DELETE", id, p.id, "");
  }

  /** 送审、撤回和独立复核都核对项目状态；批准时再次计算额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void budgetAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var budget = db.get(Budget.class, id);
    var p = access.project(budget.projectId);
    writable(p, "DRAFT", "ACTIVE");
    Rules.version(budget, b.get("version"));
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    switch (action) {
      case "submit" -> {
        access.require("budget.write");
        Rules.state(budget.state, "DRAFT");
        accounting.validateBudget(p, budget);
        budget.state = "PENDING";
        budget.submitterId = access.current().id;
      }
      case "withdraw" -> {
        access.require("budget.write");
        Rules.state(budget.state, "PENDING", "DRAFT");
        budget.state = "WITHDRAWN";
      }
      case "approve", "reject" -> {
        var u = access.require("budget.review");
        Rules.state(budget.state, "PENDING");
        Rules.separate(budget.creatorId, u.id);
        Rules.separate(budget.submitterId, u.id);
        if (action.equals("approve")) {
          accounting.validateBudget(p, budget);
          budget.state = "APPROVED";
          budget.approvedAt = clock.instant();
          p.activeBudgetId = budget.id;
        } else budget.state = "REJECTED";
        budget.reviewerId = u.id;
      }
      default -> throw new Problem(400, "预算动作无效");
    }
    access.audit("BUDGET_" + action.toUpperCase(Locale.ROOT), id, p.id, note);
  }

  /** 激活前检查合同证明、有效预算与任务日期；独立批准后冻结任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void projectAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var p = access.project(id);
    Rules.version(p, b.get("version"));
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    switch (action) {
      case "submit" -> {
        access.require("project.write");
        writable(p, "DRAFT");
        validateActivation(p);
        p.submitterId = access.current().id;
        p.state = "PENDING";
      }
      case "withdraw" -> {
        access.require("project.write");
        writable(p, "PENDING");
        p.state = "DRAFT";
      }
      case "approve", "reject" -> {
        var u = access.require("project.review");
        writable(p, "PENDING");
        Rules.separate(p.creatorId, u.id);
        Rules.separate(p.submitterId, u.id);
        if (action.equals("approve")) {
          validateActivation(p);
          p.state = "ACTIVE";
        } else p.state = "DRAFT";
      }
      default -> throw new Problem(400, "项目动作无效");
    }
    access.audit("PROJECT_" + action.toUpperCase(Locale.ROOT), id, id, note);
  }

  void validateActivation(Project p) {
    checkOwner(p.ownerId, p.departmentId);
    Rules.check(p.activeBudgetId != null, "请先批准初始预算");
    var active = db.get(Budget.class, p.activeBudgetId);
    Rules.check(active.state.equals("APPROVED"), "初始预算未批准");
    Rules.check(
        db.query(
                Budget.class,
                "from Budget where projectId=?1 and state in ('DRAFT','PENDING')",
                p.id)
            .isEmpty(),
        "请处理未决预算");
    accounting.validateBudget(p, active);
    var funds = db.query(Fund.class, "from Fund where projectId=?1", p.id);
    for (var f : funds) {
      Rules.check(f.evidenceId != null, "每个合同资金来源须关联真实证明文件");
      evidence(f.evidenceId, p.id);
    }
    var milestones = db.query(Milestone.class, "from Milestone where projectId=?1", p.id);
    Rules.check(milestones.stream().anyMatch(m -> m.required), "至少一个必需里程碑");
    for (var m : milestones)
      Rules.check(!m.dueOn.isBefore(p.startOn) && !m.dueOn.isAfter(p.endOn), "里程碑日期须在项目期间内");
  }

  /** 到账是线下凭据登记；批准前不增加资金，冲正保持原凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Receipt createReceipt(Long projectId, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("finance.write");
    var p = access.project(projectId);
    writable(p, "ACTIVE");
    var r = new Receipt();
    r.projectId = p.id;
    r.fundId = Rules.id(b.get("fundId"));
    Rules.check(db.get(Fund.class, r.fundId).projectId.equals(p.id), "资金来源跨项目");
    r.reference = Rules.voucher(b.get("reference"));
    r.amountCents = Rules.cents(b.get("amountCents"), false);
    r.bookedOn = Rules.date(b.get("bookedOn"));
    Rules.check(
        !r.bookedOn.isBefore(LocalDate.of(2000, 1, 1)) && !r.bookedOn.isAfter(LocalDate.now(clock)),
        "到账日期须为2000年至今天");
    r.evidenceId = evidence(Rules.id(b.get("evidenceId")), p.id).id;
    r.note = Rules.text(b.get("note"), 1600);
    r.reversalOf = Rules.nullableId(b.get("reversalOf"));
    if (r.reversalOf != null) {
      var original = db.get(Receipt.class, r.reversalOf);
      Rules.check(
          original.projectId.equals(p.id)
              && original.fundId.equals(r.fundId)
              && original.state.equals("APPROVED")
              && original.reversalOf == null
              && original.amountCents == r.amountCents,
          "须对原已批准到账完整冲正");
      Rules.check(
          db.query(
                  Receipt.class,
                  "from Receipt where reversalOf=?1 and state in ('PENDING','APPROVED')",
                  original.id)
              .isEmpty(),
          "原到账已有冲正");
    }
    r.state = "PENDING";
    r.creatorId = u.id;
    db.save(r);
    access.audit("RECEIPT_REGISTER", r.id, p.id, r.note);
    return r;
  }

  /** 独立核实合同上限及已使用资金，禁止使现金余额透支的冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void receiptAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("finance.review");
    var r = db.get(Receipt.class, id);
    var p = access.project(r.projectId);
    writable(p, "ACTIVE");
    Rules.version(r, b.get("version"));
    Rules.state(r.state, "PENDING");
    Rules.separate(r.creatorId, u.id);
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    Rules.check(Set.of("approve", "reject").contains(action), "到账复核动作无效");
    if (action.equals("approve")) {
      var totals = accounting.balances(p, p.activeBudgetId, null);
      long next =
          Rules.add(
              totals.receipts().getOrDefault(r.fundId, 0L),
              r.reversalOf == null ? r.amountCents : -r.amountCents);
      Rules.check(next <= db.get(Fund.class, r.fundId).awardCents, "累计实际到账超过合同金额");
      Rules.check(
          next >= Rules.add(totals.spent(r.fundId), totals.held(r.fundId)), "冲正后到账不足以覆盖支出与待审占用");
      if (r.reversalOf != null)
        Rules.check(db.get(Receipt.class, r.reversalOf).state.equals("APPROVED"), "原到账状态无效");
      r.state = "APPROVED";
    } else r.state = "REJECTED";
    r.reviewerId = u.id;
    access.audit("RECEIPT_" + action.toUpperCase(Locale.ROOT), id, p.id, note);
  }

  /** 草稿期定义任务、必需材料和到期日，激活后保留任务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Milestone saveMilestone(Long projectId, Long id, Map<String, Object> b) {
    db.writeLock();
    access.require("project.write");
    var p = access.project(projectId);
    writable(p, "DRAFT");
    var m = id == null ? new Milestone() : db.get(Milestone.class, id);
    if (id != null) {
      Rules.check(m.projectId.equals(p.id), "里程碑跨项目");
      Rules.version(m, b.get("version"));
    }
    m.projectId = p.id;
    m.code = Rules.code(b.get("code"));
    m.title = Rules.text(b.get("title"), 160);
    m.dueOn = Rules.date(b.get("dueOn"));
    Rules.check(!m.dueOn.isBefore(p.startOn) && !m.dueOn.isAfter(p.endOn), "里程碑须在项目期间内");
    m.required = Rules.flag(b.get("required"));
    m.note = Rules.optional(b.get("note"), 1600);
    if (id == null) {
      m.state = "OPEN";
      db.save(m);
    }
    access.audit("MILESTONE_SAVE", m.id, p.id, "");
    db.flush();
    return m;
  }

  /** 删除未被文件引用的草稿任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteMilestone(Long id, Object version) {
    db.writeLock();
    access.require("project.write");
    var m = db.get(Milestone.class, id);
    var p = access.project(m.projectId);
    writable(p, "DRAFT");
    Rules.version(m, version);
    db.delete(m);
    access.audit("MILESTONE_DELETE", id, p.id, "");
  }

  /** 提交冻结本轮文件；独立验收和重新编制都保留旧版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void milestoneAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var m = db.get(Milestone.class, id);
    var p = access.project(m.projectId);
    writable(p, "ACTIVE");
    Rules.version(m, b.get("version"));
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    switch (action) {
      case "submit" -> {
        var u = access.require("delivery.write");
        Rules.state(m.state, "OPEN", "CHANGES");
        var ids = documents.latest(p.id, m.id);
        Rules.check(!ids.isEmpty() && ids.size() <= 10, "每轮须有1至10个不同名称的成果文件");
        m.frozenEvidenceIds = String.join(",", ids.stream().map(Object::toString).toList());
        m.submitterId = u.id;
        m.state = "IN_REVIEW";
      }
      case "accept", "reject" -> {
        var u = access.require("delivery.review");
        Rules.state(m.state, "IN_REVIEW");
        Rules.separate(m.submitterId, u.id);
        m.reviewerId = u.id;
        m.state = action.equals("accept") ? "ACCEPTED" : "CHANGES";
      }
      case "revise" -> {
        access.require("delivery.write");
        Rules.state(m.state, "ACCEPTED");
        m.state = "OPEN";
        m.roundNumber++;
      }
      default -> throw new Problem(400, "成果动作无效");
    }
    access.audit("MILESTONE_" + action.toUpperCase(Locale.ROOT), id, p.id, note);
  }

  /** 项目延期或单个里程碑改期独立审签，审签时再次核对旧日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Amendment amend(Long projectId, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("project.write");
    var p = access.project(projectId);
    writable(p, "ACTIVE");
    Rules.check(
        db.query(Amendment.class, "from Amendment where projectId=?1 and state='PENDING'", p.id)
            .isEmpty(),
        "已有未决日期变更");
    var a = new Amendment();
    a.projectId = p.id;
    a.milestoneId = Rules.nullableId(b.get("milestoneId"));
    if (a.milestoneId != null) {
      var m = db.get(Milestone.class, a.milestoneId);
      Rules.check(m.projectId.equals(p.id), "里程碑跨项目");
      a.oldDate = m.dueOn;
    } else a.oldDate = p.endOn;
    a.newDate = Rules.date(b.get("newDate"));
    validateDate(p, a);
    a.reason = Rules.text(b.get("reason"), 1600);
    a.creatorId = u.id;
    a.state = "PENDING";
    db.save(a);
    access.audit("DATE_CHANGE_REQUEST", a.id, p.id, a.reason);
    return a;
  }

  void validateDate(Project p, Amendment a) {
    Rules.check(
        !a.newDate.isBefore(p.startOn) && !a.newDate.isAfter(p.startOn.plusYears(10)),
        "变更日期超出允许期间");
    if (a.milestoneId != null) Rules.check(!a.newDate.isAfter(p.endOn), "须先批准项目延期，再调整里程碑");
    else {
      Rules.check(a.newDate.isAfter(p.startOn), "结束日须晚于开始日");
      for (var m : db.query(Milestone.class, "from Milestone where projectId=?1", p.id))
        Rules.check(!m.dueOn.isAfter(a.newDate), "结束日早于现有里程碑");
      for (var l :
          db.query(
              Ledger.class,
              "from Ledger where projectId=?1 and state in ('PENDING','APPROVED','REVERSED')",
              p.id)) Rules.check(!l.bookedOn.isAfter(a.newDate), "结束日早于已核对凭证日期");
    }
  }

  /** 按原日期与版本独立批准或驳回日期变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void amendmentAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("project.review");
    var a = db.get(Amendment.class, id);
    var p = access.project(a.projectId);
    writable(p, "ACTIVE");
    Rules.version(a, b.get("version"));
    Rules.state(a.state, "PENDING");
    Rules.separate(a.creatorId, u.id);
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    Rules.check(Set.of("approve", "reject").contains(action), "日期复核动作无效");
    if (action.equals("approve")) {
      validateDate(p, a);
      if (a.milestoneId == null) {
        Rules.check(p.endOn.equals(a.oldDate), "原项目结束日已变更");
        p.endOn = a.newDate;
      } else {
        var m = db.get(Milestone.class, a.milestoneId);
        Rules.check(m.dueOn.equals(a.oldDate), "原里程碑日期已变更");
        m.dueOn = a.newDate;
      }
      a.state = "APPROVED";
    } else a.state = "REJECTED";
    a.reviewerId = u.id;
    access.audit("DATE_CHANGE_" + action.toUpperCase(Locale.ROOT), id, p.id, note);
  }

  /** 列出具体阻断项，负责人能在提交前处理缺口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<String> closeBlockers(Project p) {
    var reasons = new ArrayList<String>();
    if (p.activeBudgetId == null) reasons.add("没有批准预算");
    if (db.query(
                Milestone.class,
                "from Milestone where projectId=?1 and required=true and state<>'ACCEPTED'",
                p.id)
            .size()
        > 0) reasons.add("必需里程碑尚未验收");
    if (!db.query(Milestone.class, "from Milestone where projectId=?1 and state='IN_REVIEW'", p.id)
        .isEmpty()) reasons.add("仍有成果在审");
    if (!db.query(
            Budget.class, "from Budget where projectId=?1 and state in ('DRAFT','PENDING')", p.id)
        .isEmpty()) reasons.add("仍有未决预算版本");
    if (!db.query(Receipt.class, "from Receipt where projectId=?1 and state='PENDING'", p.id)
        .isEmpty()) reasons.add("仍有到账或到账冲正在审");
    if (!db.query(
            Ledger.class,
            "from Ledger where projectId=?1 and state in ('UNMATCHED','PENDING')",
            p.id)
        .isEmpty()) reasons.add("已识别项目的财务凭证尚未全部核对或排除");
    if (!db.query(
            Reconciliation.class,
            "from Reconciliation where projectId=?1 and state='PENDING'",
            p.id)
        .isEmpty()) reasons.add("仍有支出分摊在审");
    if (!db.query(Amendment.class, "from Amendment where projectId=?1 and state='PENDING'", p.id)
        .isEmpty()) reasons.add("仍有日期变更在审");
    return reasons;
  }

  String snapshot(Project p) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put(
        "project",
        new TreeMap<>(
            Map.of(
                "id",
                p.id,
                "code",
                p.code,
                "title",
                p.title,
                "startOn",
                p.startOn,
                "endOn",
                p.endOn,
                "departmentId",
                p.departmentId,
                "ownerId",
                p.ownerId,
                "activeBudgetId",
                p.activeBudgetId)));
    var funds = db.query(Fund.class, "from Fund where projectId=?1 order by id", p.id);
    var receipts =
        db.query(
            Receipt.class,
            "from Receipt where projectId=?1 and state='APPROVED' order by id",
            p.id);
    var milestones =
        db.query(Milestone.class, "from Milestone where projectId=?1 order by id", p.id);
    root.put("funds", funds);
    root.put("summary", accounting.summary(p));
    root.put(
        "budgetRevisions",
        db.query(Budget.class, "from Budget where projectId=?1 order by id", p.id));
    root.put(
        "amendments",
        db.query(Amendment.class, "from Amendment where projectId=?1 order by id", p.id));
    root.put(
        "budget",
        db.query(
            BudgetLine.class, "from BudgetLine where budgetId=?1 order by id", p.activeBudgetId));
    root.put("receipts", receipts);
    root.put("ledger", db.query(Ledger.class, "from Ledger where projectId=?1 order by id", p.id));
    var recs =
        db.query(Reconciliation.class, "from Reconciliation where projectId=?1 order by id", p.id);
    root.put("reconciliations", recs);
    root.put(
        "allocations",
        db.query(
            Allocation.class,
            "from Allocation where reconciliationId in (select r.id from Reconciliation r where r.projectId=?1 and r.state='APPROVED') order by id",
            p.id));
    root.put("milestones", milestones);
    Set<Long> ids = new TreeSet<>();
    for (var f : funds) if (f.evidenceId != null) ids.add(f.evidenceId);
    for (var r : receipts) ids.add(r.evidenceId);
    for (var m : milestones)
      if (m.state.equals("ACCEPTED") && !m.frozenEvidenceIds.isBlank())
        for (String id : m.frozenEvidenceIds.split(",")) ids.add(Long.valueOf(id));
    root.put(
        "files",
        documents.metadata(p.id).stream()
            .filter(m -> ids.contains((Long) m.get("id")))
            .map(
                m -> {
                  var v = new LinkedHashMap<String, Object>();
                  for (String key :
                      List.of(
                          "id", "fileName", "contentType", "sizeBytes", "digest", "versionNumber"))
                    v.put(key, m.get(key));
                  return v;
                })
            .toList());
    return json.writeValueAsString(root);
  }

  /** 申请结题冻结规范快照；结余须说明安排，重开保留原结题。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Closeout closeRequest(Long projectId, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("close.write");
    var p = access.project(projectId);
    String kind = Rules.text(b.get("kind"), 20);
    Rules.check(Set.of("CLOSE", "REOPEN").contains(kind), "结题类型无效");
    var c = new Closeout();
    c.projectId = p.id;
    c.kind = kind;
    c.creatorId = u.id;
    c.reason = Rules.text(b.get("reason"), 1600);
    c.residualPlan = Rules.optional(b.get("residualPlan"), 1600);
    c.createdAt = clock.instant();
    c.state = "PENDING";
    if (kind.equals("CLOSE")) {
      writable(p, "ACTIVE");
      var reasons = closeBlockers(p);
      Rules.check(reasons.isEmpty(), String.join("；", reasons));
      boolean residual =
          accounting.summary(p).stream().anyMatch(s -> !s.get("residualCents").equals("0"));
      Rules.check(!residual || !c.residualPlan.isBlank(), "请说明结余资金后续处理安排");
      c.snapshotJson = snapshot(p);
      p.state = "CLOSING";
    } else {
      writable(p, "CLOSED");
      var previous =
          db.query(
              Closeout.class,
              "from Closeout where projectId=?1 and kind='CLOSE' and state='APPROVED' order by id desc",
              p.id);
      Rules.check(!previous.isEmpty(), "无已批准结题记录");
      c.snapshotJson =
          json.writeValueAsString(
              Map.of("previousCloseoutId", previous.getFirst().id, "reason", c.reason));
      p.state = "REOPENING";
    }
    c.digest = Rules.digest(c.snapshotJson.getBytes(StandardCharsets.UTF_8));
    db.save(c);
    access.audit("CLOSEOUT_REQUEST_" + kind, c.id, p.id, c.reason);
    return c;
  }

  /** 独立结题复核再验快照一致性；重开同样须另一人批准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void closeAction(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("close.review");
    var c = db.get(Closeout.class, id);
    var p = access.project(c.projectId);
    Rules.version(c, b.get("version"));
    Rules.state(c.state, "PENDING");
    Rules.separate(c.creatorId, u.id);
    String action = Rules.text(b.get("action"), 20), note = Rules.text(b.get("note"), 1600);
    Rules.check(Set.of("approve", "reject").contains(action), "结题复核动作无效");
    writable(p, c.kind.equals("CLOSE") ? "CLOSING" : "REOPENING");
    if (action.equals("approve")) {
      if (c.kind.equals("CLOSE")) {
        Rules.check(closeBlockers(p).isEmpty(), "结题检查发生变化");
        Rules.check(
            c.digest.equals(Rules.digest(snapshot(p).getBytes(StandardCharsets.UTF_8))),
            "结题快照发生变化，请退回重新提交");
        p.state = "CLOSED";
      } else p.state = "ACTIVE";
      c.state = "APPROVED";
    } else {
      p.state = c.kind.equals("CLOSE") ? "ACTIVE" : "CLOSED";
      c.state = "REJECTED";
    }
    c.reviewerId = u.id;
    access.audit("CLOSEOUT_" + action.toUpperCase(Locale.ROOT), id, p.id, note);
  }

  /** 项目内所有关联记录统一范围返回，不携带密码或文件二进制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var p = access.project(id);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("project", p);
    m.put("funds", db.query(Fund.class, "from Fund where projectId=?1 order by id", id));
    var budgets = db.query(Budget.class, "from Budget where projectId=?1 order by id desc", id);
    m.put("budgets", budgets);
    m.put(
        "budgetLines",
        db.query(
            BudgetLine.class,
            "from BudgetLine where budgetId in (select b.id from Budget b where b.projectId=?1) order by id",
            id));
    m.put(
        "receipts",
        db.query(Receipt.class, "from Receipt where projectId=?1 order by id desc", id));
    m.put(
        "milestones",
        db.query(Milestone.class, "from Milestone where projectId=?1 order by id", id));
    m.put("files", documents.metadata(id));
    m.put("members", db.query(Member.class, "from Member where projectId=?1 order by id", id));
    m.put(
        "closeouts",
        db.query(Closeout.class, "from Closeout where projectId=?1 order by id desc", id));
    m.put(
        "amendments",
        db.query(Amendment.class, "from Amendment where projectId=?1 order by id desc", id));
    m.put("summary", accounting.summary(p));
    m.put("closeBlockers", closeBlockers(p));
    m.put("ledger", db.query(Ledger.class, "from Ledger where projectId=?1 order by id", id));
    m.put(
        "reconciliations",
        db.query(Reconciliation.class, "from Reconciliation where projectId=?1 order by id", id));
    m.put(
        "allocations",
        db.query(
            Allocation.class,
            "from Allocation where reconciliationId in (select r.id from Reconciliation r where r.projectId=?1) order by id",
            id));
    m.put("events", db.query(Audit.class, "from Audit where projectId=?1 order by id desc", id));
    return m;
  }

  /** 按可见项目计算经费、待审和期限指标，无演示统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    access.require("reports");
    var projects = access.projects();
    var rows = new ArrayList<Map<String, Object>>();
    long award = 0, received = 0, spent = 0, held = 0;
    int warning =
        Integer.parseInt(
            db.query(Setting.class, "from Setting where code='warning_days'")
                .getFirst()
                .parameterValue);
    Comparator<Map<String, Object>> dueOrder =
        Comparator.<Map<String, Object>, LocalDate>comparing(m -> (LocalDate) m.get("dueOn"))
            .thenComparingLong(m -> ((Number) m.get("id")).longValue());
    var due = new PriorityQueue<Map<String, Object>>(dueOrder.reversed());
    for (var p : projects) {
      for (var s : accounting.summary(p)) {
        var row = new LinkedHashMap<String, Object>(s);
        row.put("projectId", p.id);
        row.put("projectCode", p.code);
        row.put("projectTitle", p.title);
        row.put("state", p.state);
        rows.add(row);
        award = Rules.add(award, Long.parseLong(s.get("awardCents").toString()));
        received = Rules.add(received, Long.parseLong(s.get("receivedCents").toString()));
        spent = Rules.add(spent, Long.parseLong(s.get("spentCents").toString()));
        held = Rules.add(held, Long.parseLong(s.get("heldCents").toString()));
      }
      if (p.state.equals("ACTIVE"))
        for (var m :
            db.query(
                Milestone.class,
                "from Milestone where projectId=?1 and state<>'ACCEPTED' order by dueOn",
                p.id))
          if (!m.dueOn.isAfter(LocalDate.now(clock).plusDays(warning))) {
            due.add(
                Map.of(
                    "id",
                    m.id,
                    "projectId",
                    p.id,
                    "projectCode",
                    p.code,
                    "title",
                    m.title,
                    "dueOn",
                    m.dueOn,
                    "state",
                    m.state));
            if (due.size() > 100) due.poll();
          }
    }
    return Map.of(
        "projectCount",
        projects.size(),
        "awardCents",
        Long.toString(award),
        "receivedCents",
        Long.toString(received),
        "spentCents",
        Long.toString(spent),
        "heldCents",
        Long.toString(held),
        "rows",
        rows,
        "due",
        due.stream().sorted(dueOrder).toList(),
        "today",
        LocalDate.now(clock));
  }
}
