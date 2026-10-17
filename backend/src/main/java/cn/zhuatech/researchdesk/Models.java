// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 领域持久化实体，资金、版本、凭证及成果各有独立表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Models {
  /** 所有业务记录的身份与并发版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @MappedSuperclass
  public abstract static class Row {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version public long version;
  }

  /** 写入协调行，仅用于事务锁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Guard")
  @Table(name = "write_guard")
  public static class Guard {
    @Id public Long id;
  }

  /** 组织部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Department")
  @Table(name = "department")
  public static class Department extends Row {
    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "name", nullable = false, length = 120)
    public String name = "";

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 岗位权限及范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Role")
  @Table(name = "access_role")
  public static class Role extends Row {
    @Column(name = "name", nullable = false, length = 120)
    public String name = "";

    @Column(name = "scope", nullable = false, length = 20)
    public String scope = "";

    @Column(name = "permissions", nullable = false, length = 1000)
    public String permissions = "";
  }

  /** 实际账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Account")
  @Table(name = "account")
  public static class Account extends Row {
    @Column(name = "username", nullable = false, length = 60)
    public String username = "";

    @Column(name = "display_name", nullable = false, length = 120)
    public String displayName = "";

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash = "";

    @Column(name = "role_id", nullable = false)
    public Long roleId;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 稳定科目字典。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Subject")
  @Table(name = "dictionary_entry")
  public static class Subject extends Row {
    @Column(name = "group_code", nullable = false, length = 40)
    public String groupCode = "";

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 可配置参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Setting")
  @Table(name = "system_setting")
  public static class Setting extends Row {
    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "parameter_value", nullable = false, length = 6000)
    public String parameterValue = "";
  }

  /** 业务导航。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Menu")
  @Table(name = "nav_menu")
  public static class Menu extends Row {
    @Column(name = "code", nullable = false, length = 40)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "permission", nullable = false, length = 60)
    public String permission = "";

    @Column(name = "position", nullable = false)
    public int position = 0;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 追加审计记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Audit")
  @Table(name = "audit_event")
  public static class Audit extends Row {
    @Column(name = "actor_id", nullable = false)
    public Long actorId;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "project_id", nullable = true)
    public Long projectId;

    @Column(name = "action", nullable = false, length = 80)
    public String action = "";

    @Column(name = "object_ref", nullable = false, length = 120)
    public String objectRef = "";

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 科研项目与冻结任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Project")
  @Table(name = "research_project")
  public static class Project extends Row {
    @Column(name = "submitter_id", nullable = true)
    public Long submitterId;

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "title", nullable = false, length = 160)
    public String title = "";

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "owner_id", nullable = false)
    public Long ownerId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "start_on", nullable = false)
    public LocalDate startOn;

    @Column(name = "end_on", nullable = false)
    public LocalDate endOn;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "active_budget_id", nullable = true)
    public Long activeBudgetId;

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";
  }

  /** 成员项目关联。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Member")
  @Table(name = "project_member")
  public static class Member extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "account_id", nullable = false)
    public Long accountId;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 合同资金来源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Fund")
  @Table(name = "funding_source")
  public static class Fund extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "name", nullable = false, length = 160)
    public String name = "";

    @Column(name = "contract_reference", nullable = false, length = 120)
    public String contractReference = "";

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "award_cents", nullable = false)
    public long awardCents = 0;

    @Column(name = "evidence_id", nullable = true)
    public Long evidenceId;
  }

  /** 预算版本审批。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Budget")
  @Table(name = "budget_revision")
  public static class Budget extends Row {
    @Column(name = "submitter_id", nullable = true)
    public Long submitterId;

    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "revision_number", nullable = false)
    public int revisionNumber = 1;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "reason", nullable = false, length = 1600)
    public String reason = "";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "approved_at", nullable = true)
    public Instant approvedAt;
  }

  /** 版本内来源科目预算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "BudgetLine")
  @Table(name = "budget_line")
  public static class BudgetLine extends Row {
    @Column(name = "budget_id", nullable = false)
    public Long budgetId;

    @Column(name = "fund_id", nullable = false)
    public Long fundId;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;
  }

  /** 线下到账与追加冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Receipt")
  @Table(name = "funding_receipt")
  public static class Receipt extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "fund_id", nullable = false)
    public Long fundId;

    @Column(name = "reference", nullable = false, length = 120)
    public String reference = "";

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;

    @Column(name = "booked_on", nullable = false)
    public LocalDate bookedOn;

    @Column(name = "evidence_id", nullable = false)
    public Long evidenceId;

    @Column(name = "reversal_of", nullable = true)
    public Long reversalOf;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";
  }

  /** 财务表头映射。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "ImportMapping")
  @Table(name = "import_mapping")
  public static class ImportMapping extends Row {
    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "name", nullable = false, length = 120)
    public String name = "";

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "fields_json", nullable = false)
    public String fieldsJson = "";

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 原始财务接入批次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "ImportBatch")
  @Table(name = "import_batch")
  public static class ImportBatch extends Row {
    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "mapping_id", nullable = false)
    public Long mappingId;

    @Column(name = "file_name", nullable = false, length = 160)
    public String fileName = "";

    @Column(name = "digest", nullable = false, length = 64)
    public String digest = "";

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "mapping_snapshot", nullable = false)
    public String mappingSnapshot = "";

    @Column(name = "row_count", nullable = false)
    public int rowCount = 0;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 不可覆盖财务凭证行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Ledger")
  @Table(name = "ledger_entry")
  public static class Ledger extends Row {
    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "voucher", nullable = false, length = 120)
    public String voucher = "";

    @Column(name = "line_number", nullable = false)
    public int lineNumber = 1;

    @Column(name = "booked_on", nullable = false)
    public LocalDate bookedOn;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;

    @Column(name = "project_code", nullable = false, length = 60)
    public String projectCode = "";

    @Column(name = "category_code", nullable = false, length = 60)
    public String categoryCode = "";

    @Column(name = "narrative", nullable = false, length = 600)
    public String narrative = "";

    @Column(name = "reverse_voucher", nullable = false, length = 120)
    public String reverseVoucher = "";

    @Column(name = "reverse_line", nullable = false)
    public int reverseLine = 0;

    @Column(name = "project_id", nullable = true)
    public Long projectId;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "current_reconciliation_id", nullable = true)
    public Long currentReconciliationId;
  }

  /** 凭证分摊及排除复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Reconciliation")
  @Table(name = "reconciliation")
  public static class Reconciliation extends Row {
    @Column(name = "ledger_id", nullable = false)
    public Long ledgerId;

    @Column(name = "project_id", nullable = true)
    public Long projectId;

    @Column(name = "kind", nullable = false, length = 20)
    public String kind = "";

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "reason", nullable = false, length = 1600)
    public String reason = "";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 冻结凭证分摊行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Allocation")
  @Table(name = "allocation")
  public static class Allocation extends Row {
    @Column(name = "reconciliation_id", nullable = false)
    public Long reconciliationId;

    @Column(name = "fund_id", nullable = false)
    public Long fundId;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;
  }

  /** 成果里程碑与冻结本轮文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Milestone")
  @Table(name = "milestone")
  public static class Milestone extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "title", nullable = false, length = 160)
    public String title = "";

    @Column(name = "due_on", nullable = false)
    public LocalDate dueOn;

    @Column(name = "required", nullable = false)
    public boolean required = true;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "submitter_id", nullable = true)
    public Long submitterId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "frozen_evidence_ids", nullable = false, length = 2000)
    public String frozenEvidenceIds = "";

    @Column(name = "round_number", nullable = false)
    public int roundNumber = 1;

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";
  }

  /** 私有文件独立版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Evidence")
  @Table(name = "evidence")
  public static class Evidence extends Row {
    @Column(nullable = false)
    public boolean retired = false;

    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "milestone_id", nullable = true)
    public Long milestoneId;

    @Column(name = "file_name", nullable = false, length = 160)
    public String fileName = "";

    @Column(name = "content_type", nullable = false, length = 80)
    public String contentType = "";

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes = 0;

    @Column(name = "digest", nullable = false, length = 64)
    public String digest = "";

    @JsonIgnore
    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "content", nullable = false, columnDefinition = "longblob")
    public byte[] content;

    @Column(name = "version_number", nullable = false)
    public int versionNumber = 1;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "note", nullable = false, length = 600)
    public String note = "";
  }

  /** 项目或里程碑日期变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Amendment")
  @Table(name = "project_amendment")
  public static class Amendment extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "milestone_id", nullable = true)
    public Long milestoneId;

    @Column(name = "old_date", nullable = false)
    public LocalDate oldDate;

    @Column(name = "new_date", nullable = false)
    public LocalDate newDate;

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "reason", nullable = false, length = 1600)
    public String reason = "";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;
  }

  /** 结题及重新打开审签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Closeout")
  @Table(name = "closeout")
  public static class Closeout extends Row {
    @Column(name = "project_id", nullable = false)
    public Long projectId;

    @Column(name = "kind", nullable = false, length = 20)
    public String kind = "";

    @Column(name = "state", nullable = false, length = 20)
    public String state = "";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "reason", nullable = false, length = 1600)
    public String reason = "";

    @Column(name = "residual_plan", nullable = false, length = 1600)
    public String residualPlan = "";

    @JsonIgnore
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "snapshot_json", nullable = false)
    public String snapshotJson = "";

    @Column(name = "digest", nullable = false, length = 64)
    public String digest = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }
}
