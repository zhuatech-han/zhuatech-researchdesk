// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 财务文件原子导入、支出分摊、独立复核与完整追加冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Finance {
  final Store db;
  final Access access;
  final Accounting accounting;
  final WorkbookIO workbook;
  final Clock clock;

  public Finance(Store db, Access access, Accounting accounting, WorkbookIO workbook, Clock clock) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.workbook = workbook;
    this.clock = clock;
  }

  void role() {
    if (!access.has("finance.write") && !access.has("finance.review"))
      throw new Problem(403, "没有财务接入权限");
  }

  /** 批次按内容摘要及凭证号/行号去重；任一错误整批回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ImportBatch importFile(Long mappingId, String filename, byte[] data) {
    db.writeLock();
    var u = access.require("finance.write");
    var mapping = db.get(ImportMapping.class, mappingId);
    access.financeDepartment(mapping.departmentId);
    Rules.check(mapping.enabled, "导入映射已停用");
    var rows = workbook.read(filename, data, mapping.fieldsJson);
    String digest = Rules.digest(data);
    Rules.check(
        db.query(ImportBatch.class, "from ImportBatch where digest=?1", digest).isEmpty(),
        "此文件已导入");
    var batch = new ImportBatch();
    batch.departmentId = mapping.departmentId;
    batch.creatorId = u.id;
    batch.mappingId = mapping.id;
    batch.fileName = Documents.filename(filename);
    batch.digest = digest;
    batch.mappingSnapshot = mapping.fieldsJson;
    batch.rowCount = rows.size();
    batch.createdAt = clock.instant();
    db.save(batch);
    var keys = new HashSet<String>();
    int number = 1;
    for (var row : rows) {
      number++;
      try {
        var line = new Ledger();
        line.batchId = batch.id;
        line.departmentId = batch.departmentId;
        line.voucher = Rules.voucher(row.get("voucher"));
        line.lineNumber = Integer.parseInt(row.get("lineNumber"));
        Rules.check(line.lineNumber >= 1 && line.lineNumber <= 100000, "行号须1至100000");
        String key = line.voucher + ":" + line.lineNumber;
        Rules.check(keys.add(key), "批次内凭证号/行号重复");
        Rules.check(
            db.query(
                    Ledger.class,
                    "from Ledger where voucher=?1 and lineNumber=?2",
                    line.voucher,
                    line.lineNumber)
                .isEmpty(),
            "凭证号/行号已存在");
        line.bookedOn = Rules.date(row.get("bookedOn"));
        Rules.check(
            !line.bookedOn.isBefore(LocalDate.of(2000, 1, 1))
                && !line.bookedOn.isAfter(LocalDate.now(clock)),
            "凭证日期须为2000年至今天");
        line.amountCents = Rules.yuan(row.get("amount"));
        line.projectCode =
            row.get("projectCode").isBlank() ? "" : Rules.code(row.get("projectCode"));
        line.categoryCode =
            row.get("categoryCode").isBlank() ? "" : Rules.code(row.get("categoryCode"));
        line.narrative = Rules.optional(row.get("narrative"), 600);
        line.reverseVoucher =
            row.get("reverseVoucher").isBlank() ? "" : Rules.voucher(row.get("reverseVoucher"));
        line.reverseLine =
            row.get("reverseLine").isBlank() ? 0 : Integer.parseInt(row.get("reverseLine"));
        Rules.check(
            line.amountCents < 0
                ? (!line.reverseVoucher.isEmpty() && line.reverseLine > 0)
                : (line.reverseVoucher.isEmpty() && line.reverseLine == 0),
            "负数须填原凭证号/行号，正数不得填写");
        var ps = db.query(Project.class, "from Project where code=?1", line.projectCode);
        if (!ps.isEmpty() && access.visible(ps.getFirst())) {
          var p = ps.getFirst();
          Rules.state(p.state, "DRAFT", "ACTIVE");
          Rules.check(p.departmentId.equals(batch.departmentId), "凭证项目与导入映射部门不一致");
          line.projectId = p.id;
        }
        line.state = "UNMATCHED";
        db.save(line);
      } catch (Problem | IllegalArgumentException e) {
        throw new Problem(400, "第" + number + "行：" + e.getMessage());
      }
    }
    db.flush();
    access.audit("FINANCE_IMPORT", batch.id, null, "记录 " + batch.rowCount + " 行");
    return batch;
  }

  Ledger line(Long id) {
    role();
    var row = db.get(Ledger.class, id);
    access.financeDepartment(row.departmentId);
    if (row.projectId != null) access.project(row.projectId);
    return row;
  }

  /** 安全列表，禁止研究者借项目查看财务整批导入明细。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> workbench() {
    role();
    String scope = access.role().scope;
    var u = access.current();
    Rules.check(!scope.equals("ASSIGNED"), "本人项目范围没有财务批次访问权限");
    var rows =
        db.all(Ledger.class).stream()
            .filter(
                r ->
                    (scope.equals("ALL") || r.departmentId.equals(u.departmentId))
                        && (r.projectId == null
                            || access.visible(db.get(Project.class, r.projectId))))
            .toList();
    var ids = rows.stream().map(r -> r.id).toList();
    return Map.of(
        "entries",
        rows,
        "batches",
        db.all(ImportBatch.class).stream()
            .filter(b -> scope.equals("ALL") || b.departmentId.equals(u.departmentId))
            .toList(),
        "mappings",
        db.all(ImportMapping.class).stream()
            .filter(
                m -> m.enabled && (scope.equals("ALL") || m.departmentId.equals(u.departmentId)))
            .toList(),
        "reconciliations",
        db.all(Reconciliation.class).stream().filter(r -> ids.contains(r.ledgerId)).toList(),
        "allocations",
        db.all(Allocation.class).stream()
            .filter(
                a ->
                    db
                        .query(
                            Reconciliation.class,
                            "from Reconciliation where id=?1",
                            a.reconciliationId)
                        .stream()
                        .anyMatch(r -> ids.contains(r.ledgerId)))
            .toList());
  }

  /** 原始行不可修改；每次送审建立新的不可覆盖分摊版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Reconciliation submit(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("finance.write");
    var row = line(id);
    Rules.version(row, b.get("version"));
    Rules.state(row.state, "UNMATCHED");
    String kind = Rules.text(b.get("kind"), 20);
    Rules.check(Set.of("ALLOCATE", "REVERSE", "EXCLUDE").contains(kind), "对账类型无效");
    var r = new Reconciliation();
    r.ledgerId = id;
    r.creatorId = u.id;
    r.kind = kind;
    r.reason = Rules.text(b.get("reason"), 1600);
    r.state = "PENDING";
    r.createdAt = clock.instant();
    List<Allocation> lines = new ArrayList<>();
    if (kind.equals("EXCLUDE")) {
      r.projectId = row.projectId;
      if (r.projectId != null) Rules.state(access.project(r.projectId).state, "ACTIVE", "DRAFT");
    } else {
      var p = access.project(Rules.id(b.get("projectId")));
      Rules.state(p.state, "ACTIVE");
      Rules.check(p.departmentId.equals(row.departmentId), "凭证与项目必须归属同一部门");
      Rules.check(
          !row.bookedOn.isBefore(p.startOn) && !row.bookedOn.isAfter(p.endOn),
          "凭证日期不在项目期间，请核对或先完成日期变更");
      r.projectId = p.id;
      if (row.projectId != null && !row.projectId.equals(p.id))
        Rules.check(b.get("remapConfirmed") instanceof Boolean c && c, "变更已识别项目须明确确认并说明原因");
      if (kind.equals("ALLOCATE")) {
        Rules.check(row.amountCents > 0, "正数支出才能建立分摊");
        Rules.check(b.get("lines") instanceof List<?>, "请填写分摊");
        for (var input : (List<?>) b.get("lines")) {
          Rules.check(input instanceof Map<?, ?>, "分摊行格式错误");
          var m = (Map<?, ?>) input;
          var a = new Allocation();
          a.fundId = Rules.id(m.get("fundId"));
          a.categoryId = Rules.id(m.get("categoryId"));
          a.amountCents = Rules.cents(m.get("amountCents"), false);
          lines.add(a);
        }
        accounting.validateAllocation(p, row.amountCents, lines, null);
      } else {
        Rules.check(row.amountCents < 0, "负数凭证才能冲正");
        var original = original(row);
        Rules.check(original.projectId.equals(p.id), "冲正必须归属原项目");
        Rules.check(
            db.query(
                    Ledger.class,
                    "from Ledger where reverseVoucher=?1 and reverseLine=?2 and state in ('PENDING','APPROVED')",
                    row.reverseVoucher,
                    row.reverseLine)
                .isEmpty(),
            "原凭证已有待审或批准冲正");
        for (var old :
            db.query(
                Allocation.class,
                "from Allocation where reconciliationId=?1 order by id",
                original.currentReconciliationId)) {
          var a = new Allocation();
          a.fundId = old.fundId;
          a.categoryId = old.categoryId;
          a.amountCents = -old.amountCents;
          lines.add(a);
        }
      }
      row.projectId = p.id;
    }
    db.save(r);
    for (var a : lines) {
      a.reconciliationId = r.id;
      db.save(a);
    }
    row.currentReconciliationId = r.id;
    row.state = "PENDING";
    access.audit("RECONCILIATION_SUBMIT", r.id, r.projectId, r.reason);
    db.flush();
    return r;
  }

  Ledger original(Ledger reversal) {
    var originals =
        db.query(
            Ledger.class,
            "from Ledger where voucher=?1 and lineNumber=?2",
            reversal.reverseVoucher,
            reversal.reverseLine);
    Rules.check(originals.size() == 1, "未找到原始凭证");
    var original = originals.getFirst();
    Rules.state(original.state, "APPROVED");
    Rules.check(
        original.amountCents > 0 && original.amountCents == -reversal.amountCents,
        "只支持对已核销原支出的完整冲正");
    var rec = db.get(Reconciliation.class, original.currentReconciliationId);
    Rules.check(rec.state.equals("APPROVED") && rec.kind.equals("ALLOCATE"), "原凭证不是有效已批准支出");
    return original;
  }

  /** 独立复核再次计算预算和现金；拒绝释放预留并保留旧版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void review(Long id, Map<String, Object> b) {
    db.writeLock();
    var u = access.require("finance.review");
    var r = db.get(Reconciliation.class, id);
    var row = line(r.ledgerId);
    Rules.version(r, b.get("version"));
    Rules.state(r.state, "PENDING");
    Rules.check(
        row.currentReconciliationId.equals(id) && row.state.equals("PENDING"), "凭证当前送审版本不一致");
    Rules.separate(r.creatorId, u.id);
    String action = Rules.text(b.get("action"), 20);
    Rules.check(Set.of("approve", "reject").contains(action), "复核动作无效");
    String note = Rules.text(b.get("note"), 1600);
    Project p = r.projectId == null ? null : access.project(r.projectId);
    if (p != null)
      Rules.state(
          p.state,
          r.kind.equals("EXCLUDE") ? new String[] {"DRAFT", "ACTIVE"} : new String[] {"ACTIVE"});
    if (action.equals("approve")) {
      if (r.kind.equals("ALLOCATE"))
        accounting.validateAllocation(
            p,
            row.amountCents,
            db.query(Allocation.class, "from Allocation where reconciliationId=?1", id),
            id);
      if (r.kind.equals("REVERSE")) {
        var original = original(row);
        Rules.check(original.projectId.equals(r.projectId), "冲正项目不一致");
        original.state = "REVERSED";
      }
      r.state = "APPROVED";
      row.state = r.kind.equals("EXCLUDE") ? "EXCLUDED" : "APPROVED";
    } else {
      r.state = "REJECTED";
      row.state = "UNMATCHED";
    }
    r.reviewerId = u.id;
    access.audit("RECONCILIATION_" + action.toUpperCase(Locale.ROOT), id, r.projectId, note);
  }
}
