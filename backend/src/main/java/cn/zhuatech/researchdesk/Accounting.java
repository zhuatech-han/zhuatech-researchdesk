// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.util.*;
import org.springframework.stereotype.Service;

/** 从批准流水和待审占用计算余额，不使用可随意改写的汇总字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class Accounting {
  final Store db;

  public Accounting(Store db) {
    this.db = db;
  }

  /** 来源和科目组成不可混用的预算桶。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Key(Long fundId, Long categoryId) {}

  /** 查询时的财务快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Balances(
      Map<Key, Long> budget, Map<Key, Long> spent, Map<Key, Long> held, Map<Long, Long> receipts) {
    public long spent(Long fund) {
      return spent.entrySet().stream()
          .filter(e -> e.getKey().fundId.equals(fund))
          .mapToLong(Map.Entry::getValue)
          .reduce(0, Rules::add);
    }

    public long held(Long fund) {
      return held.entrySet().stream()
          .filter(e -> e.getKey().fundId.equals(fund))
          .mapToLong(Map.Entry::getValue)
          .reduce(0, Rules::add);
    }
  }

  /** 待审负数不会提前释放额度；复核自己时可排除当前预留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Balances balances(Project p, Long budgetId, Long excludedReconciliation) {
    var limits = new LinkedHashMap<Key, Long>();
    if (budgetId != null)
      for (var l :
          db.query(BudgetLine.class, "from BudgetLine where budgetId=?1 order by id", budgetId))
        limits.put(new Key(l.fundId, l.categoryId), l.amountCents);
    var posted = new LinkedHashMap<Key, Long>();
    var held = new LinkedHashMap<Key, Long>();
    for (var r :
        db.query(
            Reconciliation.class,
            "from Reconciliation where projectId=?1 and state in ('APPROVED','PENDING') order by id",
            p.id)) {
      if (Objects.equals(r.id, excludedReconciliation)) continue;
      for (var l :
          db.query(
              Allocation.class, "from Allocation where reconciliationId=?1 order by id", r.id)) {
        if (r.state.equals("APPROVED"))
          posted.merge(new Key(l.fundId, l.categoryId), l.amountCents, Rules::add);
        else if (l.amountCents > 0)
          held.merge(new Key(l.fundId, l.categoryId), l.amountCents, Rules::add);
      }
    }
    var receipts = new LinkedHashMap<Long, Long>();
    for (var r :
        db.query(
            Receipt.class,
            "from Receipt where projectId=?1 and state='APPROVED' order by id",
            p.id))
      receipts.merge(r.fundId, r.reversalOf == null ? r.amountCents : -r.amountCents, Rules::add);
    return new Balances(limits, posted, held, receipts);
  }

  /** 预算按来源完整覆盖合同金额，且不挤占已支出和待审支出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void validateBudget(Project p, Budget b) {
    var lines = db.query(BudgetLine.class, "from BudgetLine where budgetId=?1 order by id", b.id);
    Rules.check(!lines.isEmpty(), "预算不能为空");
    var funds = db.query(Fund.class, "from Fund where projectId=?1 order by id", p.id);
    Rules.check(!funds.isEmpty(), "请先建立资金来源");
    Map<Long, Long> totals = new HashMap<>();
    var next = new HashMap<Key, Long>();
    for (var l : lines) {
      Rules.check(l.amountCents > 0, "预算须为正数");
      var f = db.get(Fund.class, l.fundId);
      Rules.check(f.projectId.equals(p.id), "预算资金来源不属于项目");
      var c = db.get(Subject.class, l.categoryId);
      Rules.check(c.groupCode.equals("SUBJECT"), "无效预算科目");
      totals.merge(f.id, l.amountCents, Rules::add);
      Rules.check(next.put(new Key(f.id, c.id), l.amountCents) == null, "预算来源和科目不能重复");
    }
    for (var f : funds)
      Rules.check(totals.getOrDefault(f.id, 0L) == f.awardCents, "每个资金来源的预算合计须等于合同金额");
    var current = balances(p, p.activeBudgetId, null);
    var keys = new HashSet<Key>();
    keys.addAll(current.spent.keySet());
    keys.addAll(current.held.keySet());
    for (var key : keys)
      Rules.check(
          next.getOrDefault(key, 0L)
              >= Rules.add(current.spent.getOrDefault(key, 0L), current.held.getOrDefault(key, 0L)),
          "新版预算低于已支出或待审占用");
  }

  /** 支出分摊精确相等，来源/科目预算和实际到账两道额度检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void validateAllocation(Project p, long amount, List<Allocation> lines, Long exclude) {
    Rules.check(
        p.activeBudgetId != null && !lines.isEmpty() && lines.size() <= 30, "分摊须有有效预算且为1至30行");
    var b = balances(p, p.activeBudgetId, exclude);
    long total = 0;
    Map<Key, Long> buckets = new LinkedHashMap<>();
    Map<Long, Long> funds = new LinkedHashMap<>();
    for (var l : lines) {
      Rules.check(l.amountCents > 0, "支出分摊须为正数");
      var f = db.get(Fund.class, l.fundId);
      Rules.check(f.projectId.equals(p.id), "分摊资金来源跨项目");
      var c = db.get(Subject.class, l.categoryId);
      Rules.check(c.enabled && c.groupCode.equals("SUBJECT"), "科目不存在或已停用");
      var k = new Key(f.id, c.id);
      Rules.check(!buckets.containsKey(k), "同来源同科目只能有一行");
      buckets.put(k, l.amountCents);
      funds.merge(f.id, l.amountCents, Rules::add);
      total = Rules.add(total, l.amountCents);
    }
    Rules.check(total == amount, "分摊合计必须精确等于原始凭证金额");
    for (var e : buckets.entrySet()) {
      var k = e.getKey();
      long used = Rules.add(b.spent.getOrDefault(k, 0L), b.held.getOrDefault(k, 0L));
      Rules.check(Rules.add(used, e.getValue()) <= b.budget.getOrDefault(k, 0L), "资金来源/科目预算不足");
    }
    for (var e : funds.entrySet())
      Rules.check(
          Rules.add(Rules.add(b.spent(e.getKey()), b.held(e.getKey())), e.getValue())
              <= b.receipts.getOrDefault(e.getKey(), 0L),
          "该来源已到账可用资金不足");
  }

  /** 用于页面及材料包的精确金额字符串，含到账、支出、占用和余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Map<String, Object>> summary(Project p) {
    var b = balances(p, p.activeBudgetId, null);
    var out = new ArrayList<Map<String, Object>>();
    for (var f : db.query(Fund.class, "from Fund where projectId=?1 order by id", p.id)) {
      var m = new LinkedHashMap<String, Object>();
      m.put("fundId", f.id);
      m.put("fundCode", f.code);
      m.put("name", f.name);
      m.put("awardCents", Long.toString(f.awardCents));
      long received = b.receipts.getOrDefault(f.id, 0L), spent = b.spent(f.id), held = b.held(f.id);
      m.put("receivedCents", Long.toString(received));
      m.put("spentCents", Long.toString(spent));
      m.put("heldCents", Long.toString(held));
      m.put("availableCents", Long.toString(received - spent - held));
      m.put("residualCents", Long.toString(received - spent));
      out.add(m);
    }
    return out;
  }
}
