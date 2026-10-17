// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 同源业务接口，权限、数据范围和状态规则在服务层再次核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Research research;
  final Finance finance;
  final Catalog catalog;
  final Documents documents;
  final Packages packages;
  final Store db;
  final Access access;

  public ApiController(
      Research research,
      Finance finance,
      Catalog catalog,
      Documents documents,
      Packages packages,
      Store db,
      Access access) {
    this.research = research;
    this.finance = finance;
    this.catalog = catalog;
    this.documents = documents;
    this.packages = packages;
    this.db = db;
    this.access = access;
  }

  /** 读取可见业务引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/references")
  public Object references() {
    return catalog.references();
  }

  /** 读取权限范围内的资金和期限报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return research.report();
  }

  /** 搜索、筛选并分页读取项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/projects")
  public Object projects(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return research.list(q, state, sort, page, size);
  }

  /** 新建项目草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects")
  public Object create(@RequestBody Map<String, Object> b) {
    return research.saveProject(null, b);
  }

  /** 读取项目工作台详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/projects/{id}")
  public Object project(@PathVariable Long id) {
    return research.detail(id);
  }

  /** 按版本更新项目草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/projects/{id}")
  public Object update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.saveProject(id, b);
  }

  /** 删除空的未引用项目草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/projects/{id}")
  public void delete(@PathVariable Long id, @RequestParam long version) {
    research.deleteProject(id, version);
  }

  /** 执行立项送审、撤回或独立复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/actions")
  public void projectAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.projectAction(id, b);
  }

  /** 维护项目参与成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/members")
  public void member(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.member(id, b);
  }

  /** 建立合同资金来源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/funds")
  public Object fund(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.saveFund(id, null, b);
  }

  /** 修改草稿项目合同来源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/projects/{id}/funds/{fundId}")
  public Object fundUpdate(
      @PathVariable Long id, @PathVariable Long fundId, @RequestBody Map<String, Object> b) {
    return research.saveFund(id, fundId, b);
  }

  /** 删除未引用资金来源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/projects/{id}/funds/{fundId}")
  public void fundDelete(
      @PathVariable Long id, @PathVariable Long fundId, @RequestParam long version) {
    research.deleteFund(id, fundId, version);
  }

  /** 建立独立预算版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/budgets")
  public Object budget(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.createBudget(id, Rules.text(b.get("reason"), 1600));
  }

  /** 建立预算来源科目行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/budgets/{id}/lines")
  public Object budgetLine(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.saveBudgetLine(id, null, b);
  }

  /** 修改预算草稿行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/budgets/{id}/lines/{lineId}")
  public Object budgetLineUpdate(
      @PathVariable Long id, @PathVariable Long lineId, @RequestBody Map<String, Object> b) {
    return research.saveBudgetLine(id, lineId, b);
  }

  /** 删除预算草稿行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/budget-lines/{id}")
  public void budgetLineDelete(@PathVariable Long id, @RequestParam long version) {
    research.deleteBudgetLine(id, version);
  }

  /** 执行预算送审和复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/budgets/{id}/actions")
  public void budgetAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.budgetAction(id, b);
  }

  /** 登记实际到账或完整冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/receipts")
  public Object receipt(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.createReceipt(id, b);
  }

  /** 独立复核到账凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/receipts/{id}/actions")
  public void receiptAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.receiptAction(id, b);
  }

  /** 建立交付里程碑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/milestones")
  public Object milestone(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.saveMilestone(id, null, b);
  }

  /** 修改草稿交付任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/projects/{id}/milestones/{milestoneId}")
  public Object milestoneUpdate(
      @PathVariable Long id, @PathVariable Long milestoneId, @RequestBody Map<String, Object> b) {
    return research.saveMilestone(id, milestoneId, b);
  }

  /** 删除未引用草稿任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/milestones/{id}")
  public void milestoneDelete(@PathVariable Long id, @RequestParam long version) {
    research.deleteMilestone(id, version);
  }

  /** 执行成果提交、验收或重新编制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/milestones/{id}/actions")
  public void milestoneAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.milestoneAction(id, b);
  }

  /** 申请项目或任务日期变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/amendments")
  public Object amendment(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.amend(id, b);
  }

  /** 独立复核日期变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/amendments/{id}/actions")
  public void amendmentAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.amendmentAction(id, b);
  }

  /** 申请结题或重新打开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/closeouts")
  public Object closeout(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return research.closeRequest(id, b);
  }

  /** 独立复核结题或重新打开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/closeouts/{id}/actions")
  public void closeoutAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    research.closeAction(id, b);
  }

  /** 上传真实文件并创建独立版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/files")
  public Object upload(
      @PathVariable Long id,
      @RequestParam(required = false) Long milestoneId,
      @RequestParam(defaultValue = "") String note,
      @RequestParam MultipartFile file)
      throws IOException {
    return documents.upload(id, milestoneId, file.getOriginalFilename(), file.getBytes(), note);
  }

  /** 撤回未被合同或到账引用的文件版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/files/{id}/retire")
  public void retire(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    documents.retire(id, b.get("version"));
  }

  /** 私有文件仅附件下载，每次请求重新核对所属项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/files/{id}")
  public ResponseEntity<byte[]> file(@PathVariable Long id) {
    var f = documents.file(id);
    return download(f.content, f.fileName, f.contentType);
  }

  /** 导出当前项目资金表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/projects/{id}/export")
  public ResponseEntity<byte[]> export(@PathVariable Long id) {
    return download(
        packages.financialCsv(id), "researchdesk-project-" + id + ".csv", "text/csv;charset=UTF-8");
  }

  /** 下载批准结题的历史冻结材料包。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/closeouts/{id}/bundle")
  public ResponseEntity<byte[]> bundle(@PathVariable Long id) {
    return download(packages.bundle(id), "researchdesk-closeout-" + id + ".zip", "application/zip");
  }

  /** 读取部门或全部范围财务工作台。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/finance")
  public Object finance() {
    return finance.workbench();
  }

  /** 原子导入并校验财务流水批次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/finance/import")
  public Object importFile(@RequestParam Long mappingId, @RequestParam MultipartFile file)
      throws IOException {
    return finance.importFile(mappingId, file.getOriginalFilename(), file.getBytes());
  }

  /** 提交凭证分摊、完整冲正或排除说明。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/finance/entries/{id}/submit")
  public Object submit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return finance.submit(id, b);
  }

  /** 独立复核财务凭证核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/finance/reconciliations/{id}/review")
  public void review(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    finance.review(id, b);
  }

  /** 读取受控后台目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{resource}")
  public Object list(@PathVariable String resource) {
    return catalog.list(resource);
  }

  /** 新增后台目录记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{resource}")
  public Object add(@PathVariable String resource, @RequestBody Map<String, Object> b) {
    return catalog.save(resource, null, b);
  }

  /** 按版本更新后台目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{resource}/{id}")
  public Object edit(
      @PathVariable String resource, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.save(resource, id, b);
  }

  /** 删除允许删除且未引用的目录项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{resource}/{id}")
  public void remove(
      @PathVariable String resource, @PathVariable Long id, @RequestParam long version) {
    catalog.delete(resource, id, version);
  }

  /** 读取系统管理审计记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.admin();
    return db.all(Models.Audit.class).reversed();
  }

  static ResponseEntity<byte[]> download(byte[] bytes, String name, String type) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(name, java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.parseMediaType(type))
        .body(bytes);
  }
}
