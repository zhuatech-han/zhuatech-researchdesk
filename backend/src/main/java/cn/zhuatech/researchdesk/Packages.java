// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 财务导出和已批准结题材料包；历史包只读取冻结快照与原始文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(readOnly = true)
public class Packages {
  final Store db;
  final Access access;
  final Accounting accounting;
  final ObjectMapper json;

  public Packages(Store db, Access access, Accounting accounting, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.json = json;
  }

  static String row(Object... values) {
    return String.join(
            ",", Arrays.stream(values).map(v -> Rules.csv(Objects.toString(v, ""))).toList())
        + "\r\n";
  }

  static String yuan(Object cents) {
    return new BigDecimal(cents.toString()).movePointLeft(2).toPlainString();
  }

  /** 当前可见项目按资金来源导出真实金额，不附带品牌推广内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public byte[] financialCsv(Long id) {
    access.require("reports");
    var p = access.project(id);
    var b = new StringBuilder("\uFEFF");
    b.append(row("项目编号", "来源编号", "来源名称", "合同金额元", "已到账元", "已支出元", "待审占用元", "可用资金元", "结余元"));
    for (var s : accounting.summary(p))
      b.append(
          row(
              p.code,
              s.get("fundCode"),
              s.get("name"),
              yuan(s.get("awardCents")),
              yuan(s.get("receivedCents")),
              yuan(s.get("spentCents")),
              yuan(s.get("heldCents")),
              yuan(s.get("availableCents")),
              yuan(s.get("residualCents"))));
    return b.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** 已批准结题包按冻结文件摘要逐一校验，重开项目不影响旧材料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public byte[] bundle(Long id) {
    access.require("reports");
    var c = db.get(Closeout.class, id);
    access.project(c.projectId);
    Rules.check(c.kind.equals("CLOSE") && c.state.equals("APPROVED"), "仅已批准结题记录可下载材料包");
    Rules.check(
        c.digest.equals(Rules.digest(c.snapshotJson.getBytes(StandardCharsets.UTF_8))),
        "结题快照摘要不一致");
    var snapshot = json.readTree(c.snapshotJson);
    try (var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      entry(zip, "snapshot.json", c.snapshotJson.getBytes(StandardCharsets.UTF_8));
      var approvals =
          db.query(
              Audit.class,
              "from Audit where projectId=?1 and objectRef=?2 and action='CLOSEOUT_APPROVE' order by id",
              c.projectId,
              c.id.toString());
      Rules.check(
          approvals.size() == 1 && approvals.getFirst().actorId.equals(c.reviewerId), "结题批准记录不一致");
      var approved = new TreeMap<String, Object>();
      approved.put("id", c.id);
      approved.put("projectId", c.projectId);
      approved.put("creatorId", c.creatorId);
      approved.put("reviewerId", c.reviewerId);
      approved.put("createdAt", c.createdAt);
      approved.put("reason", c.reason);
      approved.put("residualPlan", c.residualPlan);
      approved.put("digest", c.digest);
      approved.put("state", c.state);
      entry(
          zip,
          "approval.json",
          json.writeValueAsBytes(
              new TreeMap<>(Map.of("closeout", approved, "approvalEvents", approvals))));
      entry(zip, "sha256.txt", (c.digest + "  snapshot.json\n").getBytes(StandardCharsets.UTF_8));
      for (String name :
          List.of(
              "funds",
              "summary",
              "budget",
              "budgetRevisions",
              "amendments",
              "receipts",
              "ledger",
              "reconciliations",
              "allocations",
              "milestones")) {
        var array = snapshot.get(name);
        if (array == null || !array.isArray() || array.isEmpty()) continue;
        var columns = new ArrayList<String>();
        array.get(0).propertyNames().forEach(columns::add);
        var csv = new StringBuilder("\uFEFF");
        csv.append(row(columns.toArray()));
        for (var item : array) {
          var cells =
              columns.stream()
                  .map(k -> item.get(k) == null || item.get(k).isNull() ? "" : item.get(k).asText())
                  .toArray();
          csv.append(row(cells));
        }
        entry(zip, name + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8));
      }
      for (var f : snapshot.get("files")) {
        var e = db.get(Evidence.class, f.get("id").asLong());
        Rules.check(
            e.projectId.equals(c.projectId)
                && e.digest.equals(f.get("digest").asText())
                && e.digest.equals(Rules.digest(e.content)),
            "冻结文件摘要不一致");
        entry(
            zip,
            "files/" + e.id + "-v" + e.versionNumber + "-" + Documents.filename(e.fileName),
            e.content);
      }
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new Problem(500, "材料包生成失败");
    }
  }

  static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
    var e = new ZipEntry(name);
    e.setTime(0);
    zip.putNextEntry(e);
    zip.write(bytes);
    zip.closeEntry();
  }
}
