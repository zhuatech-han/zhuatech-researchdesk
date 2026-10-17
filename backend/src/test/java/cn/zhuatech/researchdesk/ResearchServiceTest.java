// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** JPA、迁移、额度占用、职责分离、范围和历史结题文件的实际集成验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:researchservice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "ADMIN_PASSWORD=TEST-Only-Password123!"
    })
@Transactional
class ResearchServiceTest {
  @Autowired Store db;
  @Autowired Catalog catalog;
  @Autowired Research research;
  @Autowired Finance finance;
  @Autowired Documents documents;
  @Autowired Accounting accounting;
  @Autowired Packages packages;
  @Autowired Access access;
  Long owner, reviewer, writer, checker, outsider, project, fund1, fund2, milestone, file, budget;

  static Map<String, Object> body(Object... args) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i += 2) out.put(args[i].toString(), args[i + 1]);
    return out;
  }

  void as(Long id) {
    var a = db.get(Account.class, id);
    var auth = UsernamePasswordAuthenticationToken.authenticated(a.username, null, List.of());
    auth.setDetails(a.passwordHash);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  Long user(String username, Long role, Long dept) {
    return ((Account)
            catalog.save(
                "accounts",
                null,
                body(
                    "username",
                    username,
                    "displayName",
                    "TEST-" + username,
                    "password",
                    "TEST-Only-Password123!",
                    "roleId",
                    role,
                    "departmentId",
                    dept,
                    "enabled",
                    true)))
        .id;
  }

  static byte[] pdf() throws IOException {
    try (var d = new PDDocument();
        var b = new ByteArrayOutputStream()) {
      d.addPage(new PDPage());
      d.save(b);
      return b.toByteArray();
    }
  }

  Map<String, Object> action(Row r, String value) {
    return body("version", r.version, "action", value, "note", "TEST核实记录");
  }

  @BeforeEach
  void setup() throws Exception {
    as(1L);
    owner = user("test_owner", 2L, 1L);
    reviewer = user("test_reviewer", 3L, 1L);
    writer = user("test_writer", 4L, 1L);
    checker = user("test_checker", 5L, 1L);
    var d =
        (Department)
            catalog.save(
                "departments",
                null,
                body("code", "TEST_OTHER", "name", "TEST其他部门", "enabled", true));
    outsider = user("test_outside", 2L, d.id);
    as(owner);
    var p =
        research.saveProject(
            null,
            body(
                "code",
                "TEST-P",
                "title",
                "TEST科研项目",
                "departmentId",
                1L,
                "ownerId",
                owner,
                "startOn",
                LocalDate.now().minusDays(60).toString(),
                "endOn",
                LocalDate.now().plusDays(90).toString()));
    project = p.id;
    file = (Long) documents.upload(project, null, "TEST-contract.pdf", pdf(), "TEST合同").get("id");
    fund1 =
        research.saveFund(
                project,
                null,
                body(
                    "code",
                    "TEST-A",
                    "name",
                    "TEST委托资金",
                    "contractReference",
                    "TEST-CONTRACT-A",
                    "awardCents",
                    "20000000",
                    "evidenceId",
                    file))
            .id;
    fund2 =
        research.saveFund(
                project,
                null,
                body(
                    "code",
                    "TEST-B",
                    "name",
                    "TEST配套资金",
                    "contractReference",
                    "TEST-CONTRACT-B",
                    "awardCents",
                    "10000000",
                    "evidenceId",
                    file))
            .id;
    milestone =
        research.saveMilestone(
                project,
                null,
                body(
                    "code",
                    "TEST-M",
                    "title",
                    "TEST阶段报告",
                    "dueOn",
                    LocalDate.now().plusDays(10).toString(),
                    "required",
                    true))
            .id;
    budget = research.createBudget(project, "TEST初始预算").id;
    research.saveBudgetLine(
        budget, null, body("fundId", fund1, "categoryId", 1L, "amountCents", "20000000"));
    research.saveBudgetLine(
        budget, null, body("fundId", fund2, "categoryId", 1L, "amountCents", "10000000"));
    research.budgetAction(budget, action(db.get(Budget.class, budget), "submit"));
    as(reviewer);
    research.budgetAction(budget, action(db.get(Budget.class, budget), "approve"));
    as(owner);
    research.projectAction(project, action(p, "submit"));
    as(reviewer);
    research.projectAction(project, action(p, "approve"));
    as(writer);
    for (Long f : List.of(fund1, fund2)) {
      var r =
          research.createReceipt(
              project,
              body(
                  "fundId",
                  f,
                  "reference",
                  "TEST-RECEIPT-" + f,
                  "amountCents",
                  f.equals(fund1) ? "10000000" : "5000000",
                  "bookedOn",
                  LocalDate.now().toString(),
                  "evidenceId",
                  file,
                  "note",
                  "TEST实际到账"));
      as(checker);
      research.receiptAction(r.id, action(r, "approve"));
      as(writer);
    }
    db.flush();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  Ledger importLine(String voucher, String amount, String original) {
    as(writer);
    String line =
        voucher
            + ",1,"
            + LocalDate.now()
            + ","
            + amount
            + ",TEST-P,MATERIAL,TEST支出,"
            + original
            + ","
            + (original.isBlank() ? "" : "1")
            + "\r\n";
    var batch =
        finance.importFile(
            1L,
            "TEST.csv",
            ("凭证号,行号,日期,金额,项目编号,科目代码,摘要,原凭证号,原行号\r\n" + line).getBytes(StandardCharsets.UTF_8));
    return db.query(Ledger.class, "from Ledger where batchId=?1", batch.id).getFirst();
  }

  Reconciliation allocate(Ledger l, String a, String b) {
    as(writer);
    var lines = new ArrayList<Map<String, Object>>();
    if (a != null) lines.add(body("fundId", fund1, "categoryId", 1L, "amountCents", a));
    if (b != null) lines.add(body("fundId", fund2, "categoryId", 1L, "amountCents", b));
    return finance.submit(
        l.id,
        body(
            "version",
            l.version,
            "kind",
            "ALLOCATE",
            "projectId",
            project,
            "reason",
            "TEST按合同分摊",
            "lines",
            lines));
  }

  long total(String field) {
    return accounting.summary(db.get(Project.class, project)).stream()
        .mapToLong(m -> Long.parseLong(m.get(field).toString()))
        .sum();
  }

  void acceptMilestone() throws Exception {
    as(owner);
    documents.upload(project, milestone, "TEST-report.pdf", pdf(), "TEST报告");
    var m = db.get(Milestone.class, milestone);
    research.milestoneAction(m.id, action(m, "submit"));
    as(reviewer);
    research.milestoneAction(m.id, action(m, "accept"));
  }

  @Test
  void actualSubmitterCannotApproveCollaborativelyPreparedBudget() {
    as(owner);
    var b = research.createBudget(project, "TEST多人预算编制");
    as(1L);
    research.budgetAction(b.id, action(b, "submit"));
    assertEquals(1L, b.submitterId);
    assertThrows(Problem.class, () -> research.budgetAction(b.id, action(b, "approve")));
  }

  @Test
  void allocationHoldsCashThenPostsAfterIndependentReview() {
    var l = importLine("TEST-SPEND", "120000.00", "");
    var r = allocate(l, "8000000", "4000000");
    assertEquals(12000000, total("heldCents"));
    assertEquals(0, total("spentCents"));
    assertEquals(3000000, total("availableCents"));
    as(checker);
    finance.review(r.id, action(r, "approve"));
    assertEquals(0, total("heldCents"));
    assertEquals(12000000, total("spentCents"));
    assertTrue(
        new String(packages.financialCsv(project), StandardCharsets.UTF_8).contains("TEST-A"));
  }

  @Test
  void reservationsCannotBeDoubleSpent() {
    var l = importLine("TEST-HOLD", "90000.00", "");
    allocate(l, "9000000", null);
    var other = importLine("TEST-OVER", "20000.00", "");
    assertThrows(Problem.class, () -> allocate(other, "2000000", null));
    assertEquals(9000000, total("heldCents"));
  }

  @Test
  void rejectedAllocationReleasesHoldAndRetainsHistory() {
    var l = importLine("TEST-REJECT", "100.01", "");
    var r = allocate(l, "10001", null);
    as(checker);
    finance.review(r.id, action(r, "reject"));
    assertEquals("UNMATCHED", l.state);
    assertEquals(0, total("heldCents"));
    assertEquals("REJECTED", r.state);
    assertEquals(
        1, db.query(Allocation.class, "from Allocation where reconciliationId=?1", r.id).size());
  }

  @Test
  void exactSplitRequired() {
    var l = importLine("TEST-MISMATCH", "100.01", "");
    assertThrows(Problem.class, () -> allocate(l, "10000", null));
  }

  @Test
  void completeReversalCopiesOriginalSplitAndCannotRepeat() {
    var l = importLine("TEST-ORIGINAL", "100.01", "");
    var r = allocate(l, "5001", "5000");
    as(checker);
    finance.review(r.id, action(r, "approve"));
    var reverse = importLine("TEST-REV", "-100.01", "TEST-ORIGINAL");
    var rr =
        finance.submit(
            reverse.id,
            body(
                "version",
                reverse.version,
                "kind",
                "REVERSE",
                "projectId",
                project,
                "reason",
                "TEST完整冲正"));
    assertEquals(10001, total("spentCents"));
    as(checker);
    finance.review(rr.id, action(rr, "approve"));
    assertEquals(0, total("spentCents"));
    assertEquals("REVERSED", l.state);
    var duplicate = importLine("TEST-REV2", "-100.01", "TEST-ORIGINAL");
    assertThrows(
        Problem.class,
        () ->
            finance.submit(
                duplicate.id,
                body(
                    "version",
                    duplicate.version,
                    "kind",
                    "REVERSE",
                    "projectId",
                    project,
                    "reason",
                    "TEST重复")));
  }

  @Test
  void newBudgetCannotSqueezePendingSpend() {
    var l = importLine("TEST-BUDGET", "90000.00", "");
    allocate(l, "9000000", null);
    as(owner);
    var b = research.createBudget(project, "TEST调剂");
    var first =
        db.query(BudgetLine.class, "from BudgetLine where budgetId=?1 and fundId=?2", b.id, fund1)
            .getFirst();
    research.saveBudgetLine(
        b.id,
        first.id,
        body(
            "version", first.version, "fundId", fund1, "categoryId", 1L, "amountCents", "8000000"));
    research.saveBudgetLine(
        b.id, null, body("fundId", fund1, "categoryId", 2L, "amountCents", "12000000"));
    assertThrows(Problem.class, () -> research.budgetAction(b.id, action(b, "submit")));
    assertEquals(budget, db.get(Project.class, project).activeBudgetId);
  }

  @Test
  void ownershipScopeAppliesToFilesExportAndProjectSearch() {
    as(outsider);
    assertTrue(access.projects().isEmpty());
    assertEquals(404, assertThrows(Problem.class, () -> research.detail(project)).status);
    assertEquals(404, assertThrows(Problem.class, () -> documents.file(file)).status);
    assertEquals(404, assertThrows(Problem.class, () -> packages.financialCsv(project)).status);
  }

  @Test
  void sameActorCannotApproveEvenAdministrator() {
    as(1L);
    var l = importLine("TEST-SELF", "10.00", "");
    as(1L);
    var r =
        finance.submit(
            l.id,
            body(
                "version",
                l.version,
                "kind",
                "ALLOCATE",
                "projectId",
                project,
                "reason",
                "TEST管理员经办",
                "lines",
                List.of(body("fundId", fund1, "categoryId", 1L, "amountCents", "1000"))));
    assertThrows(Problem.class, () -> finance.review(r.id, action(r, "approve")));
  }

  @Test
  void disabledAccountAndDepartmentTakeEffectImmediately() {
    as(owner);
    var a = db.get(Account.class, owner);
    a.enabled = false;
    assertEquals(401, assertThrows(Problem.class, access::current).status);
    a.enabled = true;
    db.get(Department.class, 1L).enabled = false;
    assertEquals(403, assertThrows(Problem.class, access::current).status);
  }

  @Test
  void closeBlocksMissingDeliverablesAndUnmatchedLedger() {
    as(owner);
    assertTrue(research.closeBlockers(db.get(Project.class, project)).contains("必需里程碑尚未验收"));
    importLine("TEST-UNMATCHED", "1.00", "");
    assertTrue(
        research.closeBlockers(db.get(Project.class, project)).stream()
            .anyMatch(v -> v.contains("尚未全部核对")));
  }

  @Test
  void frozenCloseoutSurvivesReopenAndNewFileVersions() throws Exception {
    acceptMilestone();
    as(owner);
    var c =
        research.closeRequest(
            project, body("kind", "CLOSE", "reason", "TEST结题", "residualPlan", "TEST结余按合同安排处理"));
    as(reviewer);
    research.closeAction(c.id, action(c, "approve"));
    byte[] original = packages.bundle(c.id);
    assertTrue(original.length > 0);
    assertEquals("CLOSED", db.get(Project.class, project).state);
    as(owner);
    assertThrows(
        Problem.class,
        () -> documents.upload(project, milestone, "TEST-report.pdf", pdf(), "TEST关闭后禁止"));
    var reopen = research.closeRequest(project, body("kind", "REOPEN", "reason", "TEST纠错重开"));
    as(reviewer);
    research.closeAction(reopen.id, action(reopen, "approve"));
    as(owner);
    var m = db.get(Milestone.class, milestone);
    research.milestoneAction(m.id, action(m, "revise"));
    documents.upload(project, milestone, "TEST-report.pdf", pdf(), "TEST新版本");
    assertArrayEquals(original, packages.bundle(c.id));
    try (var zip = new ZipInputStream(new ByteArrayInputStream(original))) {
      boolean snapshot = false, proof = false;
      for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
        if (e.getName().equals("snapshot.json")) snapshot = true;
        if (e.getName().startsWith("files/")) proof = true;
      }
      assertTrue(snapshot && proof);
    }
  }

  @Test
  void acceptedMilestoneFreezesSubmittedFileIds() throws Exception {
    acceptMilestone();
    as(owner);
    var m = db.get(Milestone.class, milestone);
    String prior = m.frozenEvidenceIds;
    assertThrows(
        Problem.class,
        () -> documents.upload(project, milestone, "TEST-report.pdf", pdf(), "TEST冻结"));
    research.milestoneAction(m.id, action(m, "revise"));
    documents.upload(project, milestone, "TEST-report.pdf", pdf(), "TEST补充");
    assertEquals(prior, m.frozenEvidenceIds);
    assertEquals(
        2,
        documents.metadata(project).stream()
            .filter(v -> Objects.equals(v.get("milestoneId"), milestone))
            .count());
  }

  @Test
  void staleVersionRejected() {
    as(owner);
    var p = db.get(Project.class, project);
    assertEquals(
        409,
        assertThrows(
                Problem.class,
                () ->
                    research.projectAction(
                        p.id, body("version", p.version + 1, "action", "submit", "note", "TEST旧页")))
            .status);
  }
}
