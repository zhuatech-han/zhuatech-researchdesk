<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, shallowRef } from "vue";
import DataGrid from "./DataGrid.vue";
import FormDialog from "./FormDialog.vue";
import {
  request,
  download,
  money,
  yuan,
  cents,
  states,
  mappingKeys,
} from "./domain.js";
const profile = ref(null),
  loading = ref(true),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  route = ref("overview"),
  tab = ref("funds"),
  username = ref(""),
  password = ref("");
const refs = ref({ departments: [], people: [], categories: [], projects: [] }),
  report = ref({ rows: [], due: [] }),
  list = ref({ items: [], total: 0 }),
  detail = ref(null),
  finance = ref({
    entries: [],
    reconciliations: [],
    mappings: [],
    batches: [],
    allocations: [],
  }),
  admin = ref({}),
  adminTab = ref("accounts"),
  query = ref(""),
  filter = ref(""),
  sort = ref("id"),
  page = ref(1),
  dialog = shallowRef(null),
  formError = ref("");
const has = (p) => profile.value?.permissions.includes(p),
  me = computed(() => profile.value?.account.id),
  p = computed(() => detail.value?.project),
  draft = computed(() => p.value?.state === "DRAFT"),
  active = computed(() => p.value?.state === "ACTIVE");
const appName = computed(
  () =>
    profile.value?.settings.find((s) => s.code === "app_name")
      ?.parameterValue || "知华 ResearchDesk",
);
const title = computed(() =>
  route.value === "project"
    ? p.value?.title
    : profile.value?.menus.find((m) => m.code === route.value)?.label ||
      "科研协同",
);
const fields = (items) =>
  items.map(([key, label, type = "text", options]) => ({
    key,
    label,
    type,
    options,
  }));
const opts = (rows, label = "name") =>
  rows.map((r) => ({ value: r.id, label: r[label] || r.code }));
const category = (id) =>
  refs.value.categories.find((c) => c.id === id)?.label || `科目 #${id}`;
const person = (id) =>
  refs.value.people.find((c) => c.id === id)?.name || `账号 #${id}`;
const fundName = (id) =>
  detail.value?.funds.find((f) => f.id === id)?.name || `来源 #${id}`;
const projectName = (id) =>
  refs.value.projects.find((v) => v.id === id)?.title || "未识别项目";
const mutable = computed(() => ["DRAFT", "ACTIVE"].includes(p.value?.state));
const pendingFinance = computed(() =>
  finance.value.reconciliations
    .filter((r) => r.state === "PENDING")
    .map((r) => ({
      ...r,
      entry: finance.value.entries.find((e) => e.id === r.ledgerId),
    })),
);
const canReview = (row, perm, creator = "creatorId") =>
  has(perm) && row[creator] !== me.value && row.submitterId !== me.value;
const budgetLines = (id) =>
  detail.value.budgetLines.filter((l) => l.budgetId === id);
const genericFiles = computed(
  () => detail.value?.files.filter((f) => !f.retired) || [],
);
function fail(e) {
  error.value = e.message;
  if (e.status === 401) {
    profile.value = null;
    clearData();
  }
  if ([403, 404].includes(e.status)) {
    clearData();
    route.value = profile.value?.menus[0]?.code || "empty";
  }
}
function clearData() {
  refs.value = { departments: [], people: [], categories: [], projects: [] };
  report.value = { rows: [], due: [] };
  list.value = { items: [], total: 0 };
  detail.value = null;
  finance.value = {
    entries: [],
    reconciliations: [],
    mappings: [],
    batches: [],
    allocations: [],
  };
  admin.value = {};
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function refresh() {
  profile.value = await request("/auth/me");
  refs.value = await request("/references");
  if (has("reports")) report.value = await request("/reports");
  else report.value = { rows: [], due: [] };
  if (route.value === "projects")
    list.value = await request(
      `/projects?q=${encodeURIComponent(query.value)}&state=${filter.value}&sort=${sort.value}&page=${page.value}&size=12`,
    );
  if (route.value === "project" && p.value)
    detail.value = await request(`/projects/${p.value.id}`);
  if (["finance", "review"].includes(route.value))
    finance.value = await request("/finance");
  if (route.value === "admin") {
    const keys = [
      "accounts",
      "roles",
      "departments",
      "dictionary",
      "menus",
      "settings",
      "mappings",
      "permissions",
    ];
    const values = await Promise.all(keys.map((k) => request("/admin/" + k)));
    admin.value = Object.fromEntries(keys.map((k, i) => [k, values[i]]));
    admin.value.audit = await request("/audit");
  }
}
async function navigate(value) {
  route.value = value;
  detail.value = null;
  await run(refresh);
}
async function openProject(id) {
  await run(async () => {
    detail.value = await request("/projects/" + id);
    route.value = "project";
    tab.value = "funds";
  });
}
async function login() {
  await run(async () => {
    clearData();
    profile.value = await request("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });
    password.value = "";
    route.value = profile.value.menus[0]?.code || "overview";
    await refresh();
  });
  password.value = "";
}
async function logout() {
  await run(async () => {
    await request("/auth/logout", { method: "POST" });
    profile.value = null;
    clearData();
  });
}
function form(title, fs, values, save, message = "", submit = "保存") {
  formError.value = "";
  dialog.value = { title, fields: fs, values, save, message, submit };
}
async function saveForm(values) {
  if (busy.value) return;
  busy.value = true;
  formError.value = "";
  try {
    const current = dialog.value;
    await current.save(values);
    if (dialog.value === current) {
      dialog.value = null;
      notice.value = "操作已保存";
      if (profile.value) await refresh();
    }
  } catch (e) {
    formError.value = e.message;
    if (e.status === 401) {
      dialog.value = null;
      fail(e);
    }
  } finally {
    busy.value = false;
  }
}
function action(path, row, action, label) {
  form(
    label,
    fields([["note", "处理说明", "textarea"]]),
    { version: row.version, action, note: "" },
    (v) =>
      request(path, {
        method: "POST",
        body: { ...v, version: row.version, action },
      }),
    "处理记录将保留在项目审计中。",
    label,
  );
}
function remove(path, row, label) {
  form(
    label,
    [],
    {},
    async () => {
      await request(`${path}?version=${row.version}`, { method: "DELETE" });
      if (path === `/projects/${p.value?.id}`) {
        route.value = "projects";
        detail.value = null;
      }
    },
    "只允许删除未被引用的草稿记录；删除后不可恢复。",
    label,
  );
}
function projectForm(row) {
  form(
    row ? "编辑项目" : "新建科研项目",
    fields([
      ["code", "项目编号"],
      ["title", "项目名称"],
      ["departmentId", "承担部门", "select", opts(refs.value.departments)],
      ["ownerId", "负责人", "select", opts(refs.value.people)],
      ["startOn", "开始日期", "date"],
      ["endOn", "结束日期", "date"],
      ["note", "项目说明", "textarea"],
    ]).map((f) => ({
      ...f,
      required: f.key !== "note",
      disabled: !!row && ["code", "departmentId", "ownerId"].includes(f.key),
    })),
    row || {
      departmentId: profile.value.account.departmentId,
      ownerId: me.value,
      note: "",
    },
    async (v) => {
      const r = await request(row ? "/projects/" + row.id : "/projects", {
        method: row ? "PUT" : "POST",
        body: v,
      });
      detail.value = await request("/projects/" + r.id);
      route.value = "project";
      tab.value = "funds";
    },
  );
}
function fundForm(row) {
  form(
    row ? "编辑资金来源" : "新增资金来源",
    fields([
      ["code", "来源编号"],
      ["name", "资金来源名称"],
      ["contractReference", "合同或批复编号"],
      ["amount", "合同金额（元）"],
      [
        "evidenceId",
        "合同证明",
        "select",
        opts(genericFiles.value, "fileName"),
      ],
    ]).map((f) => ({ ...f, required: f.key !== "evidenceId" })),
    row ? { ...row, amount: yuan(row.awardCents) } : {},
    (v) =>
      request(`/projects/${p.value.id}/funds${row ? "/" + row.id : ""}`, {
        method: row ? "PUT" : "POST",
        body: { ...v, awardCents: cents(v.amount) },
      }),
    "请先在“文件与历史”上传合同文件，立项送审前每个来源须有真实证明。",
  );
}
function budgetForm() {
  form(
    "创建预算版本",
    fields([["reason", "编制或调整原因", "textarea"]]),
    {},
    (v) =>
      request(`/projects/${p.value.id}/budgets`, { method: "POST", body: v }),
  );
}
function budgetLineForm(b, row) {
  form(
    row ? "编辑预算行" : "新增预算行",
    fields([
      ["fundId", "资金来源", "select", opts(detail.value.funds)],
      [
        "categoryId",
        "预算科目",
        "select",
        opts(refs.value.categories, "label"),
      ],
      ["amount", "预算金额（元）"],
    ]),
    row ? { ...row, amount: yuan(row.amountCents) } : {},
    (v) =>
      request(`/budgets/${b.id}/lines${row ? "/" + row.id : ""}`, {
        method: row ? "PUT" : "POST",
        body: { ...v, amountCents: cents(v.amount) },
      }),
  );
}
function receiptForm(original) {
  form(
    original ? "登记到账完整冲正" : "登记实际到账",
    fields([
      ["fundId", "资金来源", "select", opts(detail.value.funds)],
      ["reference", "到账凭证编号"],
      ["amount", "金额（元）"],
      ["bookedOn", "到账日期", "date"],
      [
        "evidenceId",
        "实际凭据",
        "select",
        opts(genericFiles.value, "fileName"),
      ],
      ["note", "核对说明", "textarea"],
    ]).map((f) => ({
      ...f,
      disabled: !!original && ["fundId", "amount"].includes(f.key),
    })),
    original
      ? {
          fundId: original.fundId,
          amount: yuan(original.amountCents),
          bookedOn: report.value.today,
        }
      : { bookedOn: report.value.today },
    (v) =>
      request(`/projects/${p.value.id}/receipts`, {
        method: "POST",
        body: {
          ...v,
          amountCents: cents(v.amount),
          reversalOf: original?.id || null,
        },
      }),
    "登记不会移动资金；另一人批准后才计入可用资金。",
  );
}
function milestoneForm(row) {
  form(
    row ? "编辑里程碑" : "新增里程碑",
    fields([
      ["code", "任务编号"],
      ["title", "里程碑名称"],
      ["dueOn", "截止日期", "date"],
      ["required", "结题必需", "checkbox"],
      ["note", "成果要求", "textarea"],
    ]).map((f) => ({ ...f, required: f.key !== "note" })),
    row || { required: true, note: "" },
    (v) =>
      request(`/projects/${p.value.id}/milestones${row ? "/" + row.id : ""}`, {
        method: row ? "PUT" : "POST",
        body: v,
      }),
  );
}
function upload(milestone) {
  form(
    milestone ? "上传成果版本" : "上传项目凭据",
    [
      {
        key: "file",
        label: "PDF / PNG / JPEG",
        type: "file",
        accept: ".pdf,.png,.jpg,.jpeg",
      },
      ...fields([["note", "文件说明", "textarea"]]).map((f) => ({
        ...f,
        required: false,
      })),
    ],
    {},
    (v) => {
      const body = new FormData();
      body.append("file", v.file);
      body.append("note", v.note || "");
      if (milestone) body.append("milestoneId", milestone.id);
      return request(`/projects/${p.value.id}/files`, { method: "POST", body });
    },
    milestone
      ? `任务：${milestone.title}。同名上传保留旧版，提交验收时冻结当前版本。`
      : "每文件限3MB，项目容量由管理员配置。",
  );
}
function amendForm() {
  form(
    "申请日期变更",
    [
      {
        key: "milestoneId",
        label: "变更对象",
        type: "select",
        required: false,
        options: opts(detail.value.milestones, "title"),
      },
      ...fields([
        ["newDate", "新截止日期", "date"],
        ["reason", "变更原因", "textarea"],
      ]),
    ],
    {},
    (v) =>
      request(`/projects/${p.value.id}/amendments`, {
        method: "POST",
        body: v,
      }),
    "不选里程碑表示调整项目结束日期；均须独立复核。",
  );
}
function closeForm(kind) {
  form(
    kind === "CLOSE" ? "申请结题" : "申请重新打开",
    fields([
      ["reason", "申请原因", "textarea"],
      ["residualPlan", "结余资金处理安排", "textarea"],
    ]).map((f) => ({ ...f, required: f.key === "reason" })),
    {},
    (v) =>
      request(`/projects/${p.value.id}/closeouts`, {
        method: "POST",
        body: { ...v, kind },
      }),
    kind === "CLOSE"
      ? "申请后冻结项目写入，另一人批准后生成可下载的结题材料包。"
      : "重新打开须独立批准，旧结题材料包保持原样。",
  );
}
function memberForm() {
  form(
    "维护项目成员",
    fields([
      [
        "accountId",
        "参与人员",
        "select",
        opts(refs.value.people.filter((v) => v.id !== p.value.ownerId)),
      ],
      ["enabled", "保留参与权限", "checkbox"],
    ]),
    { enabled: true },
    (v) =>
      request(`/projects/${p.value.id}/members`, { method: "POST", body: v }),
  );
}
function importForm() {
  form(
    "导入财务流水",
    [
      {
        key: "mappingId",
        label: "部门与表头映射",
        type: "select",
        options: opts(finance.value.mappings),
      },
      {
        key: "file",
        label: "CSV / XLSX 文件",
        type: "file",
        accept: ".csv,.xlsx",
      },
    ],
    {},
    (v) => {
      const body = new FormData();
      body.append("file", v.file);
      body.append("mappingId", v.mappingId);
      return request("/finance/import", { method: "POST", body });
    },
    "每文件限1MB、500条记录；重复凭证、公式或错误行会使整个批次退回。",
  );
}
async function reconcile(row, kind) {
  if (kind === "EXCLUDE") {
    form(
      "申请排除凭证",
      fields([["reason", "与科研业务无关的原因", "textarea"]]),
      {},
      (v) =>
        request(`/finance/entries/${row.id}/submit`, {
          method: "POST",
          body: { ...v, version: row.version, kind },
        }),
    );
    return;
  }
  const projects = refs.value.projects.filter((p) => p.state === "ACTIVE");
  form(
    "选择凭证归属项目",
    [
      {
        key: "projectId",
        label: "归属项目",
        type: "select",
        options: opts(projects, "title"),
      },
    ],
    { projectId: row.projectId || "" },
    async (v) => {
      const d = await request("/projects/" + v.projectId);
      form(
        kind === "REVERSE" ? "提交完整冲正" : "核对支出分摊",
        [
          ...(kind === "ALLOCATE"
            ? [
                {
                  key: "lines",
                  label: "来源与科目分摊（元）",
                  type: "allocations",
                  funds: opts(d.funds),
                  categories: opts(refs.value.categories, "label"),
                },
              ]
            : []),
          ...(row.projectId && row.projectId !== v.projectId
            ? [
                {
                  key: "remapConfirmed",
                  label: "确认变更已识别项目",
                  type: "checkbox",
                  required: false,
                },
              ]
            : []),
          ...fields([["reason", "核对说明", "textarea"]]),
        ],
        {
          projectId: v.projectId,
          lines: [
            {
              fundId: d.funds[0]?.id || "",
              categoryId: refs.value.categories[0]?.id || "",
              amount: yuan(row.amountCents),
            },
          ],
          remapConfirmed: false,
        },
        (values) =>
          request(`/finance/entries/${row.id}/submit`, {
            method: "POST",
            body: {
              ...values,
              version: row.version,
              kind,
              lines:
                kind === "ALLOCATE"
                  ? values.lines.map((l) => ({
                      ...l,
                      amountCents: cents(l.amount),
                    }))
                  : [],
            },
          }),
        `项目：${d.project.title}\n凭证：${row.voucher} / ${row.lineNumber}\n原始金额：${money(row.amountCents)} 元\n${kind === "REVERSE" ? "冲正沿用原凭证的完整分摊。" : "分摊合计须精确相等，并满足每个来源的预算与到账额度。"}`,
      );
    },
    "请选择实际承担支出的科研项目。",
    "下一步",
  );
}
async function auditDetail(row) {
  await run(async () => {
    const d = row.projectId
      ? await request("/projects/" + row.projectId)
      : null;
    const lines = finance.value.allocations
      .filter((a) => a.reconciliationId === row.id)
      .map(
        (a) =>
          `${d?.funds.find((f) => f.id === a.fundId)?.name || "来源 #" + a.fundId} · ${category(a.categoryId)}：${money(a.amountCents)} 元`,
      );
    form(
      "凭证与分摊明细",
      [],
      {},
      async () => {},
      `凭证：${row.entry?.voucher || "—"} / ${row.entry?.lineNumber || "—"}\n日期：${row.entry?.bookedOn || "—"}\n金额：${money(row.entry?.amountCents)} 元\n项目：${d?.project.title || "未关联"}\n核对类型：${states[row.kind]}\n经办说明：${row.reason}\n\n${lines.join("\n") || "无分摊行"}`,
      "关闭",
    );
  });
}
function passwordForm() {
  form(
    "修改本人密码",
    fields([
      ["oldPassword", "当前密码", "password"],
      ["newPassword", "新密码", "password"],
    ]),
    {},
    async (v) => {
      await request("/auth/password", { method: "POST", body: v });
      profile.value = null;
    },
    "新密码须12至72字节，含大小写字母和数字；保存后重新登录。",
  );
}
const adminNames = {
  accounts: "账号",
  roles: "角色与权限",
  departments: "部门",
  dictionary: "预算科目",
  menus: "菜单",
  settings: "系统参数",
  mappings: "财务表头映射",
  audit: "操作审计",
};
function adminForm(resource, row) {
  let fs = [],
    v = row ? { ...row } : { enabled: true };
  const depts = opts(admin.value.departments),
    roles = opts(admin.value.roles);
  if (resource === "accounts")
    fs = fields([
      ["username", "登录名"],
      ["displayName", "姓名"],
      ["departmentId", "部门", "select", depts],
      ["roleId", "角色", "select", roles],
      ["password", row ? "重设密码（留空不变）" : "初始密码", "password"],
      ["enabled", "启用", "checkbox"],
    ]).map((f) => ({
      ...f,
      disabled: !!row && f.key === "username",
      required: !(row && f.key === "password"),
    }));
  if (resource === "roles") {
    fs = fields([
      ["name", "角色名称"],
      [
        "scope",
        "可见数据范围",
        "select",
        Object.entries(states)
          .filter(([k]) => ["ALL", "DEPARTMENT", "ASSIGNED"].includes(k))
          .map(([value, label]) => ({ value, label })),
      ],
      [
        "permissions",
        "操作权限",
        "checks",
        admin.value.permissions.map((x) => ({ value: x.code, label: x.label })),
      ],
    ]).map((f) => ({ ...f, disabled: !!row && f.key === "scope" }));
    v.permissions = row ? row.permissions.split(",") : [];
    v.scope = row?.scope || "DEPARTMENT";
  }
  if (resource === "departments")
    fs = fields([
      ["code", "部门代码"],
      ["name", "部门名称"],
      ["enabled", "启用", "checkbox"],
    ]);
  if (resource === "dictionary")
    fs = fields([
      ["code", "科目代码"],
      ["label", "科目名称"],
      ["enabled", "启用", "checkbox"],
    ]);
  if (resource === "menus")
    fs = fields([
      ["label", "菜单名称"],
      ["position", "显示顺序", "number"],
      ["enabled", "启用", "checkbox"],
    ]);
  if (resource === "settings") fs = fields([["parameterValue", row.label]]);
  if (resource === "mappings") {
    fs = fields([
      ["code", "映射代码"],
      ["name", "映射名称"],
      ["departmentId", "归属部门", "select", depts],
      ["enabled", "启用", "checkbox"],
      ...Object.entries(mappingKeys).map(([key, label]) => [
        "map_" + key,
        label + "列的实际表头",
      ]),
    ]);
    const map = row ? JSON.parse(row.fieldsJson) : mappingKeys;
    for (const [key, value] of Object.entries(map)) v["map_" + key] = value;
  }
  fs = fs.map((f) => ({
    ...f,
    disabled:
      f.disabled ||
      (!!row && ["code"].includes(f.key)) ||
      (!!row && resource === "mappings" && f.key === "departmentId"),
  }));
  form((row ? "修改" : "新增") + adminNames[resource], fs, v, (values) => {
    const body = { ...values };
    if (resource === "mappings")
      body.fieldsJson = JSON.stringify(
        Object.fromEntries(
          Object.keys(mappingKeys).map((k) => [k, values["map_" + k]]),
        ),
      );
    return request(`/admin/${resource}${row ? "/" + row.id : ""}`, {
      method: row ? "PUT" : "POST",
      body,
    });
  });
}
const adminColumns = computed(() =>
  (
    ({
      accounts: [
        ["username", "登录名"],
        ["displayName", "姓名"],
        [
          "roleId",
          "角色",
          (v) => admin.value.roles.find((r) => r.id === v)?.name,
        ],
        [
          "departmentId",
          "部门",
          (v) => admin.value.departments.find((r) => r.id === v)?.name,
        ],
        ["enabled", "启用"],
      ],
      roles: [
        ["name", "角色"],
        ["scope", "数据范围", (v) => states[v]],
        [
          "permissions",
          "权限",
          (v) =>
            v
              .split(",")
              .map(
                (k) =>
                  admin.value.permissions.find((p) => p.code === k)?.label || k,
              )
              .join("、"),
        ],
      ],
      departments: [
        ["code", "代码"],
        ["name", "部门"],
        ["enabled", "启用"],
      ],
      dictionary: [
        ["code", "代码"],
        ["label", "科目"],
        ["enabled", "启用"],
      ],
      menus: [
        ["code", "入口"],
        ["label", "名称"],
        ["position", "顺序"],
        ["enabled", "启用"],
      ],
      settings: [
        ["label", "参数"],
        ["parameterValue", "当前值"],
      ],
      mappings: [
        ["code", "代码"],
        ["name", "映射"],
        [
          "departmentId",
          "部门",
          (v) => admin.value.departments.find((r) => r.id === v)?.name,
        ],
        ["enabled", "启用"],
      ],
      audit: [
        ["createdAt", "时间"],
        ["actorId", "操作账号"],
        ["action", "动作"],
        ["objectRef", "记录"],
        ["note", "说明"],
      ],
    })[adminTab.value] || []
  ).map(([key, label, format]) => ({ key, label, format })),
);
function about() {
  form(
    "关于 ResearchDesk",
    [],
    {},
    async () => {},
    "知华科技（上海如静知华信息科技有限公司）\n官网：https://www.zhuatech.cn/\n源码公开、非商业使用，仅限个人学习交流。未经公司授权不得商用。商业授权、部署或深度定制请联系微信 zhuatech / zhuatech2。",
    "关闭",
  );
}
onMounted(async () => {
  try {
    profile.value = await request("/auth/me");
    route.value = profile.value.menus[0]?.code || "empty";
    await refresh();
  } catch (e) {
    if (e.status !== 401) fail(e);
  } finally {
    loading.value = false;
  }
});
</script>
<template>
  <div v-if="loading" class="boot">正在载入 ResearchDesk…</div>
  <main v-else-if="!profile" class="login-page">
    <section class="login-story">
      <a
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        class="login-brand"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span>知华科技</span></a
      >
      <div class="eyebrow">RESEARCHDESK</div>
      <h1>科研项目经费<br />与履约协同</h1>
      <p>从合同与预算，到资金核对、成果验收和结题归档。</p>
      <div class="story-line">
        <span>01 合同资金</span><span>02 经费核对</span><span>03 成果结题</span>
      </div>
      <small>公开源码学习版 · 非商业使用</small>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <div class="eyebrow">机构工作台</div>
        <h2>登录 ResearchDesk</h2>
        <p class="muted">使用机构分配的账号进入工作台</p>
        <form @submit.prevent="login">
          <label
            >登录名<input
              v-model="username"
              required
              autocomplete="username"
              maxlength="60"
              autofocus /></label
          ><label
            >密码<input
              v-model="password"
              required
              type="password"
              autocomplete="current-password"
              maxlength="128"
          /></label>
          <p v-if="error" class="error" role="alert">{{ error }}</p>
          <button :disabled="busy">
            {{ busy ? "正在登录…" : "登录工作台 →" }}
          </button>
        </form>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技 · 官网与商业咨询 ↗</a
        >
      </div>
    </section>
  </main>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a
        href="https://www.zhuatech.cn/"
        class="brand"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span
          >ResearchDesk<small>知华科研协同</small></span
        ></a
      >
      <div class="sidebar-caption">机构工作台</div>
      <nav aria-label="主导航">
        <button
          v-for="m in profile.menus"
          :key="m.id"
          :class="{
            selected:
              route === m.code ||
              (route === 'project' && m.code === 'projects'),
          }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          <span class="nav-dot"></span>{{ m.label }}
        </button>
      </nav>
      <div class="sidebar-bottom">
        <span class="edition">源码公开 · 非商业使用</span
        ><button class="text-button" @click="about">关于与授权</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技 ↗</a
        >
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div>
          <span class="top-label">{{ appName }}</span
          ><span class="separator">/</span><span>{{ title }}</span>
        </div>
        <div class="account">
          <span
            >{{ profile.account.displayName
            }}<small
              >{{ profile.role.name }} · {{ states[profile.role.scope] }}</small
            ></span
          ><button class="text-button" @click="passwordForm">改密码</button
          ><button class="text-button" :disabled="busy" @click="logout">
            退出
          </button>
        </div>
      </header>
      <main class="content" :aria-busy="busy">
        <div v-if="error" class="banner error" role="alert">
          {{ error
          }}<button class="text-button" @click="error = ''">关闭</button>
        </div>
        <div v-if="notice" class="banner success" role="status">
          {{ notice }}
        </div>
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              {{ route === "project" ? p.code : "RESEARCH OPERATIONS" }}
            </div>
            <h1>{{ title }}</h1>
            <p v-if="route === 'overview'">
              合同、到账、支出与交付，按当前数据范围汇总。
            </p>
            <p v-else-if="route === 'finance'">
              文件接入与原始凭证核对，待审分摊实时占用额度。
            </p>
            <p v-else-if="route === 'review'">
              经办与复核分离，核准前请检查原始凭证及资金分摊。
            </p>
            <p v-else-if="route === 'reports'">
              人民币元 · 已批准记录计入支出，待审金额单列。
            </p>
            <p v-else-if="route === 'project'">
              {{ p.startOn }} — {{ p.endOn }} · 负责人 {{ person(p.ownerId) }}
              <span class="badge" :data-state="p.state">{{
                states[p.state]
              }}</span>
            </p>
          </div>
          <div class="heading-actions">
            <button
              v-if="route === 'projects' && has('project.write')"
              :disabled="busy"
              @click="projectForm()"
            >
              ＋ 新建项目</button
            ><button
              v-if="route === 'finance' && has('finance.write')"
              :disabled="busy"
              @click="importForm"
            >
              导入流水</button
            ><button
              v-if="route === 'project'"
              class="secondary"
              @click="navigate('projects')"
            >
              返回项目</button
            ><button class="secondary" :disabled="busy" @click="run(refresh)">
              刷新
            </button>
          </div>
        </div>
        <template v-if="route === 'overview' || route === 'reports'"
          ><div v-if="has('reports')" class="metrics">
            <article>
              <span>合同资金</span
              ><strong>¥ {{ money(report.awardCents) }}</strong
              ><small>{{ report.projectCount || 0 }} 个可见项目</small>
            </article>
            <article>
              <span>实际到账</span
              ><strong>¥ {{ money(report.receivedCents) }}</strong
              ><small>独立核实的到账净额</small>
            </article>
            <article>
              <span>已核对支出</span
              ><strong>¥ {{ money(report.spentCents) }}</strong
              ><small>含已批准完整冲正</small>
            </article>
            <article>
              <span>待审分摊</span
              ><strong class="amber">¥ {{ money(report.heldCents) }}</strong
              ><small>同时预留预算与资金</small>
            </article>
          </div>
          <section v-if="route === 'overview'" class="panel">
            <div class="section-heading">
              <h2>近期交付与逾期任务</h2>
              <span class="muted">截至 {{ report.today || "今天" }}</span>
            </div>
            <DataGrid
              :rows="report.due || []"
              :columns="[
                { key: 'projectCode', label: '项目编号' },
                { key: 'title', label: '里程碑' },
                { key: 'dueOn', label: '截止日期' },
                { key: 'state', label: '状态', state: true },
              ]"
              empty="当前没有临近到期或逾期的未验收任务"
              ><template #actions="{ row }"
                ><button
                  class="link-button"
                  @click="openProject(row.projectId)"
                >
                  查看项目
                </button></template
              ></DataGrid
            >
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>项目资金视图</h2>
              <span class="muted">按资金来源分列</span>
            </div>
            <DataGrid
              :rows="report.rows || []"
              :columns="[
                { key: 'projectCode', label: '项目' },
                { key: 'name', label: '资金来源' },
                { key: 'awardCents', label: '合同金额', money: true },
                { key: 'receivedCents', label: '到账净额', money: true },
                { key: 'spentCents', label: '已支出', money: true },
                { key: 'heldCents', label: '待审占用', money: true },
                { key: 'availableCents', label: '当前可用', money: true },
                { key: 'state', label: '项目状态', state: true },
              ]"
              ><template #actions="{ row }"
                ><button
                  class="link-button"
                  @click="openProject(row.projectId)"
                >
                  打开</button
                ><button
                  v-if="has('reports')"
                  class="link-button"
                  @click="
                    run(() =>
                      download(
                        '/projects/' + row.projectId + '/export',
                        row.projectCode + '-资金.csv',
                      ),
                    )
                  "
                >
                  导出
                </button></template
              ></DataGrid
            >
          </section></template
        >
        <template v-else-if="route === 'projects'"
          ><form
            class="toolbar"
            @submit.prevent="
              page = 1;
              run(refresh);
            "
          >
            <input
              v-model="query"
              placeholder="搜索项目编号或名称"
              aria-label="搜索项目"
            /><select v-model="filter" aria-label="项目状态">
              <option value="">全部状态</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'PENDING',
                  'ACTIVE',
                  'CLOSING',
                  'CLOSED',
                  'REOPENING',
                ]"
                :key="s"
                :value="s"
              >
                {{ states[s] }}
              </option></select
            ><select v-model="sort" aria-label="排序">
              <option value="id">最新建立</option>
              <option value="code">编号降序</option>
              <option value="title">名称降序</option></select
            ><button class="secondary" :disabled="busy">查询</button>
          </form>
          <section class="panel">
            <DataGrid
              :rows="list.items"
              :columns="[
                { key: 'code', label: '项目编号' },
                { key: 'title', label: '项目名称' },
                { key: 'ownerId', label: '负责人', format: person },
                { key: 'startOn', label: '开始日' },
                { key: 'endOn', label: '结束日' },
                { key: 'state', label: '状态', state: true },
              ]"
              empty="暂无项目，可新建项目并编制合同来源、预算与里程碑"
              ><template #actions="{ row }"
                ><button class="link-button" @click="openProject(row.id)">
                  工作台 →
                </button></template
              ></DataGrid
            >
            <footer class="pagination">
              <span>共 {{ list.total }} 个项目</span
              ><button
                class="secondary"
                :disabled="page <= 1 || busy"
                @click="
                  page--;
                  run(refresh);
                "
              >
                上一页</button
              ><span>{{ page }}</span
              ><button
                class="secondary"
                :disabled="page * 12 >= list.total || busy"
                @click="
                  page++;
                  run(refresh);
                "
              >
                下一页
              </button>
            </footer>
          </section></template
        >
        <template v-else-if="route === 'project' && detail"
          ><div class="project-actions">
            <button
              v-if="draft && has('project.write')"
              class="secondary"
              @click="projectForm(p)"
            >
              编辑项目</button
            ><button
              v-if="draft && has('project.write')"
              @click="
                action(
                  '/projects/' + p.id + '/actions',
                  p,
                  'submit',
                  '提交立项',
                )
              "
            >
              提交立项</button
            ><button
              v-if="p.state === 'PENDING' && has('project.write')"
              class="secondary"
              @click="
                action(
                  '/projects/' + p.id + '/actions',
                  p,
                  'withdraw',
                  '撤回立项',
                )
              "
            >
              撤回立项</button
            ><template
              v-if="p.state === 'PENDING' && canReview(p, 'project.review')"
              ><button
                @click="
                  action(
                    '/projects/' + p.id + '/actions',
                    p,
                    'approve',
                    '批准立项',
                  )
                "
              >
                批准立项</button
              ><button
                class="secondary"
                @click="
                  action(
                    '/projects/' + p.id + '/actions',
                    p,
                    'reject',
                    '退回立项',
                  )
                "
              >
                退回
              </button></template
            ><button
              v-if="active && has('project.write')"
              class="secondary"
              @click="amendForm"
            >
              申请日期变更</button
            ><button
              v-if="has('reports')"
              class="secondary"
              @click="
                run(() =>
                  download(
                    '/projects/' + p.id + '/export',
                    p.code + '-资金.csv',
                  ),
                )
              "
            >
              导出资金表</button
            ><button
              v-if="draft && has('project.write')"
              class="danger-text"
              @click="remove('/projects/' + p.id, p, '删除空草稿')"
            >
              删除空草稿
            </button>
          </div>
          <div class="tabs">
            <button
              v-for="[key, label] in [
                ['funds', '合同资金'],
                ['budgets', '预算版本'],
                ['receipts', '实际到账'],
                ['ledger', '财务核对'],
                ['milestones', '成果与任务'],
                ['closeouts', '结题与变更'],
                ['files', '文件与历史'],
              ]"
              :key="key"
              :class="{ selected: tab === key }"
              @click="tab = key"
            >
              {{ label }}
            </button>
          </div>
          <section v-if="tab === 'funds'" class="panel">
            <div class="section-heading">
              <h2>资金来源</h2>
              <button v-if="draft && has('project.write')" @click="fundForm()">
                新增来源
              </button>
            </div>
            <DataGrid
              :rows="detail.funds"
              :columns="[
                { key: 'code', label: '来源编号' },
                { key: 'name', label: '来源名称' },
                { key: 'contractReference', label: '合同或批复' },
                { key: 'awardCents', label: '合同金额', money: true },
                {
                  key: 'evidenceId',
                  label: '证明文件',
                  format: (v) => (v ? '已关联 #' + v : '待补充'),
                },
              ]"
              ><template #actions="{ row }"
                ><button
                  v-if="row.evidenceId"
                  class="link-button"
                  @click="
                    run(() =>
                      download(
                        '/files/' + row.evidenceId,
                        detail.files.find((f) => f.id === row.evidenceId)
                          ?.fileName || '合同凭据',
                      ),
                    )
                  "
                >
                  下载凭据</button
                ><template v-if="draft && has('project.write')"
                  ><button class="link-button" @click="fundForm(row)">
                    编辑</button
                  ><button
                    class="link-button danger-text"
                    @click="
                      remove(
                        '/projects/' + p.id + '/funds/' + row.id,
                        row,
                        '删除来源',
                      )
                    "
                  >
                    删除
                  </button></template
                ></template
              ></DataGrid
            >
            <h3 class="subheading">执行余额</h3>
            <DataGrid
              :rows="detail.summary"
              :columns="[
                { key: 'name', label: '来源' },
                { key: 'receivedCents', label: '到账净额', money: true },
                { key: 'spentCents', label: '已支出', money: true },
                { key: 'heldCents', label: '待审占用', money: true },
                { key: 'availableCents', label: '当前可用', money: true },
                { key: 'residualCents', label: '账面结余', money: true },
              ]"
            ></DataGrid>
          </section>
          <template v-else-if="tab === 'budgets'"
            ><div class="section-heading">
              <h2>预算版本</h2>
              <button v-if="mutable && has('budget.write')" @click="budgetForm">
                创建预算版本
              </button>
            </div>
            <section v-for="b in detail.budgets" :key="b.id" class="panel">
              <div class="section-heading">
                <div>
                  <h2>
                    第 {{ b.revisionNumber }} 版
                    <span class="badge" :data-state="b.state">{{
                      states[b.state]
                    }}</span>
                    <span v-if="p.activeBudgetId === b.id" class="current-label"
                      >当前有效</span
                    >
                  </h2>
                  <p class="muted">{{ b.reason }}</p>
                </div>
                <div class="heading-actions">
                  <template
                    v-if="mutable && has('budget.write') && b.state === 'DRAFT'"
                    ><button class="secondary" @click="budgetLineForm(b)">
                      新增预算行</button
                    ><button
                      @click="
                        action(
                          '/budgets/' + b.id + '/actions',
                          b,
                          'submit',
                          '提交预算',
                        )
                      "
                    >
                      提交复核
                    </button></template
                  ><button
                    v-if="
                      mutable &&
                      has('budget.write') &&
                      ['DRAFT', 'PENDING'].includes(b.state)
                    "
                    class="secondary"
                    @click="
                      action(
                        '/budgets/' + b.id + '/actions',
                        b,
                        'withdraw',
                        '撤回预算',
                      )
                    "
                  >
                    撤回</button
                  ><template
                    v-if="
                      mutable &&
                      b.state === 'PENDING' &&
                      canReview(b, 'budget.review')
                    "
                    ><button
                      @click="
                        action(
                          '/budgets/' + b.id + '/actions',
                          b,
                          'approve',
                          '批准预算',
                        )
                      "
                    >
                      批准</button
                    ><button
                      class="secondary"
                      @click="
                        action(
                          '/budgets/' + b.id + '/actions',
                          b,
                          'reject',
                          '驳回预算',
                        )
                      "
                    >
                      驳回
                    </button></template
                  >
                </div>
              </div>
              <DataGrid
                :rows="budgetLines(b.id)"
                :columns="[
                  { key: 'fundId', label: '资金来源', format: fundName },
                  { key: 'categoryId', label: '科目', format: category },
                  { key: 'amountCents', label: '金额（元）', money: true },
                ]"
                ><template #actions="{ row }"
                  ><template
                    v-if="mutable && b.state === 'DRAFT' && has('budget.write')"
                    ><button
                      class="link-button"
                      @click="budgetLineForm(b, row)"
                    >
                      编辑</button
                    ><button
                      class="link-button danger-text"
                      @click="
                        remove('/budget-lines/' + row.id, row, '删除预算行')
                      "
                    >
                      删除
                    </button></template
                  ></template
                ></DataGrid
              >
            </section>
            <div v-if="!detail.budgets.length" class="empty panel">
              尚未编制预算
            </div></template
          >
          <section v-else-if="tab === 'receipts'" class="panel">
            <div class="section-heading">
              <h2>实际到账与冲正</h2>
              <button
                v-if="active && has('finance.write')"
                @click="receiptForm()"
              >
                登记到账
              </button>
            </div>
            <DataGrid
              :rows="detail.receipts"
              :columns="[
                { key: 'reference', label: '凭证号' },
                { key: 'fundId', label: '来源', format: fundName },
                { key: 'bookedOn', label: '到账日期' },
                { key: 'amountCents', label: '金额（元）', money: true },
                { key: 'reversalOf', label: '原到账记录' },
                { key: 'state', label: '状态', state: true },
                { key: 'note', label: '说明' },
              ]"
              ><template #actions="{ row }"
                ><button
                  class="link-button"
                  @click="
                    run(() =>
                      download(
                        '/files/' + row.evidenceId,
                        detail.files.find((f) => f.id === row.evidenceId)
                          ?.fileName || '到账凭据',
                      ),
                    )
                  "
                >
                  凭据</button
                ><template
                  v-if="
                    active &&
                    row.state === 'PENDING' &&
                    canReview(row, 'finance.review')
                  "
                  ><button
                    class="link-button"
                    @click="
                      action(
                        '/receipts/' + row.id + '/actions',
                        row,
                        'approve',
                        '核实到账',
                      )
                    "
                  >
                    核实</button
                  ><button
                    class="link-button"
                    @click="
                      action(
                        '/receipts/' + row.id + '/actions',
                        row,
                        'reject',
                        '驳回到账',
                      )
                    "
                  >
                    驳回
                  </button></template
                ><button
                  v-if="
                    active &&
                    row.state === 'APPROVED' &&
                    !row.reversalOf &&
                    has('finance.write') &&
                    !detail.receipts.some(
                      (r) =>
                        r.reversalOf === row.id &&
                        ['PENDING', 'APPROVED'].includes(r.state),
                    )
                  "
                  class="link-button"
                  @click="receiptForm(row)"
                >
                  完整冲正
                </button></template
              ></DataGrid
            >
          </section>
          <section v-else-if="tab === 'ledger'" class="panel">
            <div class="section-heading"><h2>项目财务凭证</h2></div>
            <DataGrid
              :rows="detail.ledger"
              :columns="[
                { key: 'voucher', label: '凭证号' },
                { key: 'lineNumber', label: '行号' },
                { key: 'bookedOn', label: '日期' },
                { key: 'amountCents', label: '金额（元）', money: true },
                { key: 'narrative', label: '摘要' },
                { key: 'state', label: '状态', state: true },
              ]"
            ></DataGrid>
            <h3 class="subheading">分摊与核对历史</h3>
            <DataGrid
              :rows="detail.reconciliations"
              :columns="[
                { key: 'id', label: '核对编号' },
                { key: 'ledgerId', label: '流水编号' },
                { key: 'kind', label: '类型', state: true },
                { key: 'reason', label: '核对原因' },
                { key: 'state', label: '状态', state: true },
              ]"
            ></DataGrid
            ><DataGrid
              :rows="detail.allocations"
              :columns="[
                { key: 'reconciliationId', label: '核对编号' },
                { key: 'fundId', label: '资金来源', format: fundName },
                { key: 'categoryId', label: '科目', format: category },
                { key: 'amountCents', label: '分摊金额（元）', money: true },
              ]"
            ></DataGrid>
          </section>
          <template v-else-if="tab === 'milestones'"
            ><div class="section-heading">
              <h2>成果与交付里程碑</h2>
              <button
                v-if="draft && has('project.write')"
                @click="milestoneForm()"
              >
                新增里程碑
              </button>
            </div>
            <section
              v-for="m in detail.milestones"
              :key="m.id"
              class="panel milestone"
            >
              <div class="section-heading">
                <div>
                  <span class="eyebrow"
                    >{{ m.code }} · 第 {{ m.roundNumber }} 轮</span
                  >
                  <h2>
                    {{ m.title }}
                    <span class="badge" :data-state="m.state">{{
                      states[m.state]
                    }}</span>
                  </h2>
                  <p class="muted">
                    {{ m.dueOn }} 截止 ·
                    {{ m.required ? "结题必需" : "选填任务" }} · {{ m.note }}
                  </p>
                </div>
                <div class="heading-actions">
                  <template v-if="draft && has('project.write')"
                    ><button class="secondary" @click="milestoneForm(m)">
                      编辑</button
                    ><button
                      class="secondary"
                      @click="remove('/milestones/' + m.id, m, '删除里程碑')"
                    >
                      删除
                    </button></template
                  ><template
                    v-if="
                      active &&
                      has('delivery.write') &&
                      ['OPEN', 'CHANGES'].includes(m.state)
                    "
                    ><button class="secondary" @click="upload(m)">
                      上传版本</button
                    ><button
                      @click="
                        action(
                          '/milestones/' + m.id + '/actions',
                          m,
                          'submit',
                          '提交成果验收',
                        )
                      "
                    >
                      提交验收
                    </button></template
                  ><template
                    v-if="
                      active &&
                      m.state === 'IN_REVIEW' &&
                      canReview(m, 'delivery.review', 'submitterId')
                    "
                    ><button
                      @click="
                        action(
                          '/milestones/' + m.id + '/actions',
                          m,
                          'accept',
                          '通过成果验收',
                        )
                      "
                    >
                      验收通过</button
                    ><button
                      class="secondary"
                      @click="
                        action(
                          '/milestones/' + m.id + '/actions',
                          m,
                          'reject',
                          '退回补充成果',
                        )
                      "
                    >
                      退回补充
                    </button></template
                  ><button
                    v-if="
                      active && m.state === 'ACCEPTED' && has('delivery.write')
                    "
                    class="secondary"
                    @click="
                      action(
                        '/milestones/' + m.id + '/actions',
                        m,
                        'revise',
                        '重新编制成果',
                      )
                    "
                  >
                    重新编制
                  </button>
                </div>
              </div>
              <DataGrid
                :rows="detail.files.filter((f) => f.milestoneId === m.id)"
                :columns="[
                  { key: 'fileName', label: '文件' },
                  { key: 'versionNumber', label: '版本' },
                  {
                    key: 'sizeBytes',
                    label: '大小',
                    format: (v) => (v / 1024).toFixed(1) + ' KB',
                  },
                  {
                    key: 'id',
                    label: '最近提交引用',
                    format: (v) =>
                      m.frozenEvidenceIds.split(',').includes(String(v))
                        ? '已冻结'
                        : '—',
                  },
                  { key: 'retired', label: '已撤回' },
                ]"
                ><template #actions="{ row }"
                  ><button
                    class="link-button"
                    @click="
                      run(() => download('/files/' + row.id, row.fileName))
                    "
                  >
                    下载</button
                  ><button
                    v-if="
                      active &&
                      has('delivery.write') &&
                      ['OPEN', 'CHANGES'].includes(m.state) &&
                      !row.retired
                    "
                    class="link-button"
                    @click="
                      action(
                        '/files/' + row.id + '/retire',
                        row,
                        'retire',
                        '撤回文件',
                      )
                    "
                  >
                    撤回
                  </button></template
                ></DataGrid
              >
            </section>
            <div v-if="!detail.milestones.length" class="empty panel">
              尚未定义交付里程碑
            </div></template
          >
          <template v-else-if="tab === 'closeouts'"
            ><section class="panel close-readiness">
              <div class="section-heading">
                <h2>结题检查</h2>
                <button
                  v-if="active && has('close.write')"
                  :disabled="detail.closeBlockers.length > 0"
                  @click="closeForm('CLOSE')"
                >
                  申请结题</button
                ><button
                  v-if="p.state === 'CLOSED' && has('close.write')"
                  class="secondary"
                  @click="closeForm('REOPEN')"
                >
                  申请重新打开
                </button>
              </div>
              <ul v-if="detail.closeBlockers.length">
                <li v-for="b in detail.closeBlockers" :key="b">{{ b }}</li>
              </ul>
              <p v-else class="success-text">
                业务检查已满足；如有结余，请在申请中说明后续处理安排。
              </p>
            </section>
            <section class="panel">
              <div class="section-heading"><h2>结题与重开记录</h2></div>
              <DataGrid
                :rows="detail.closeouts"
                :columns="[
                  { key: 'id', label: '记录号' },
                  { key: 'kind', label: '类型', state: true },
                  { key: 'reason', label: '申请原因' },
                  { key: 'residualPlan', label: '结余安排' },
                  { key: 'state', label: '状态', state: true },
                ]"
                ><template #actions="{ row }"
                  ><template
                    v-if="
                      row.state === 'PENDING' && canReview(row, 'close.review')
                    "
                    ><button
                      class="link-button"
                      @click="
                        action(
                          '/closeouts/' + row.id + '/actions',
                          row,
                          'approve',
                          '批准申请',
                        )
                      "
                    >
                      批准</button
                    ><button
                      class="link-button"
                      @click="
                        action(
                          '/closeouts/' + row.id + '/actions',
                          row,
                          'reject',
                          '驳回申请',
                        )
                      "
                    >
                      驳回
                    </button></template
                  ><button
                    v-if="
                      row.state === 'APPROVED' &&
                      row.kind === 'CLOSE' &&
                      has('reports')
                    "
                    class="link-button"
                    @click="
                      run(() =>
                        download(
                          '/closeouts/' + row.id + '/bundle',
                          p.code + '-结题-' + row.id + '.zip',
                        ),
                      )
                    "
                  >
                    下载冻结材料包
                  </button></template
                ></DataGrid
              >
            </section>
            <section class="panel">
              <div class="section-heading"><h2>日期变更记录</h2></div>
              <DataGrid
                :rows="detail.amendments"
                :columns="[
                  {
                    key: 'milestoneId',
                    label: '对象',
                    format: (v) =>
                      v
                        ? detail.milestones.find((m) => m.id === v)?.title
                        : '项目结束日期',
                  },
                  { key: 'oldDate', label: '原日期' },
                  { key: 'newDate', label: '新日期' },
                  { key: 'reason', label: '原因' },
                  { key: 'state', label: '状态', state: true },
                ]"
                ><template #actions="{ row }"
                  ><template
                    v-if="
                      active &&
                      row.state === 'PENDING' &&
                      canReview(row, 'project.review')
                    "
                    ><button
                      class="link-button"
                      @click="
                        action(
                          '/amendments/' + row.id + '/actions',
                          row,
                          'approve',
                          '批准日期变更',
                        )
                      "
                    >
                      批准</button
                    ><button
                      class="link-button"
                      @click="
                        action(
                          '/amendments/' + row.id + '/actions',
                          row,
                          'reject',
                          '驳回日期变更',
                        )
                      "
                    >
                      驳回
                    </button></template
                  ></template
                ></DataGrid
              >
            </section></template
          >
          <template v-else-if="tab === 'files'"
            ><section class="panel">
              <div class="section-heading">
                <h2>项目文件与全部版本</h2>
                <button
                  v-if="
                    mutable && (has('project.write') || has('finance.write'))
                  "
                  @click="upload()"
                >
                  上传凭据
                </button>
              </div>
              <DataGrid
                :rows="detail.files"
                :columns="[
                  { key: 'fileName', label: '文件名' },
                  {
                    key: 'milestoneId',
                    label: '用途',
                    format: (v) => (v ? '成果 #' + v : '合同或到账凭据'),
                  },
                  { key: 'versionNumber', label: '版本' },
                  {
                    key: 'digest',
                    label: 'SHA-256',
                    format: (v) => v.slice(0, 16) + '…',
                  },
                  { key: 'retired', label: '已撤回' },
                ]"
                ><template #actions="{ row }"
                  ><button
                    class="link-button"
                    @click="
                      run(() => download('/files/' + row.id, row.fileName))
                    "
                  >
                    下载</button
                  ><button
                    v-if="
                      mutable &&
                      !row.retired &&
                      !row.milestoneId &&
                      (has('project.write') || has('finance.write'))
                    "
                    class="link-button"
                    @click="
                      action(
                        '/files/' + row.id + '/retire',
                        row,
                        'retire',
                        '撤回文件',
                      )
                    "
                  >
                    撤回
                  </button></template
                ></DataGrid
              >
            </section>
            <section class="panel">
              <div class="section-heading">
                <h2>项目成员</h2>
                <button
                  v-if="mutable && has('project.write')"
                  class="secondary"
                  @click="memberForm"
                >
                  维护成员
                </button>
              </div>
              <p class="inner-note">负责人：{{ person(p.ownerId) }}</p>
              <DataGrid
                :rows="detail.members"
                :columns="[
                  { key: 'accountId', label: '参与人员', format: person },
                  { key: 'enabled', label: '保留参与权限' },
                ]"
              ></DataGrid>
            </section>
            <section class="panel">
              <div class="section-heading"><h2>项目操作历史</h2></div>
              <DataGrid
                :rows="detail.events"
                :columns="[
                  { key: 'createdAt', label: '时间' },
                  { key: 'actorId', label: '操作人', format: person },
                  { key: 'action', label: '动作' },
                  { key: 'objectRef', label: '记录' },
                  { key: 'note', label: '处理说明' },
                ]"
              ></DataGrid></section
          ></template>
        </template>
        <template v-else-if="route === 'finance' || route === 'review'"
          ><section class="panel">
            <div class="section-heading">
              <h2>{{ route === "review" ? "待复核记录" : "原始流水" }}</h2>
              <span class="muted"
                >{{
                  route === "review"
                    ? pendingFinance.length
                    : finance.entries.length
                }}
                条</span
              >
            </div>
            <DataGrid
              v-if="route === 'finance'"
              :rows="finance.entries"
              :columns="[
                { key: 'voucher', label: '凭证号' },
                { key: 'lineNumber', label: '行号' },
                { key: 'bookedOn', label: '日期' },
                { key: 'projectId', label: '识别项目', format: projectName },
                { key: 'amountCents', label: '金额（元）', money: true },
                { key: 'narrative', label: '摘要' },
                { key: 'state', label: '状态', state: true },
              ]"
              ><template #actions="{ row }"
                ><template
                  v-if="has('finance.write') && row.state === 'UNMATCHED'"
                  ><button
                    class="link-button"
                    @click="
                      reconcile(
                        row,
                        row.amountCents.startsWith('-')
                          ? 'REVERSE'
                          : 'ALLOCATE',
                      )
                    "
                  >
                    {{
                      row.amountCents.startsWith("-") ? "冲正" : "分摊"
                    }}</button
                  ><button
                    class="link-button"
                    @click="reconcile(row, 'EXCLUDE')"
                  >
                    排除
                  </button></template
                ><button
                  v-if="row.projectId"
                  class="link-button"
                  @click="openProject(row.projectId)"
                >
                  项目
                </button></template
              ></DataGrid
            ><DataGrid
              v-else
              :rows="pendingFinance"
              :columns="[
                { key: 'ledgerId', label: '流水编号' },
                { key: 'kind', label: '核对类型', state: true },
                { key: 'projectId', label: '项目', format: projectName },
                { key: 'reason', label: '经办说明' },
                { key: 'creatorId', label: '经办人', format: person },
              ]"
              ><template #actions="{ row }"
                ><button class="link-button" @click="auditDetail(row)">
                  凭证明细</button
                ><template v-if="canReview(row, 'finance.review')"
                  ><button
                    class="link-button"
                    @click="
                      action(
                        '/finance/reconciliations/' + row.id + '/review',
                        row,
                        'approve',
                        '批准财务核对',
                      )
                    "
                  >
                    批准</button
                  ><button
                    class="link-button"
                    @click="
                      action(
                        '/finance/reconciliations/' + row.id + '/review',
                        row,
                        'reject',
                        '驳回财务核对',
                      )
                    "
                  >
                    驳回
                  </button></template
                ><span v-else class="muted">待他人复核</span></template
              ></DataGrid
            >
          </section>
          <section class="panel">
            <div class="section-heading"><h2>导入批次</h2></div>
            <DataGrid
              :rows="finance.batches"
              :columns="[
                { key: 'id', label: '批次编号' },
                { key: 'fileName', label: '原文件名' },
                { key: 'rowCount', label: '记录数' },
                { key: 'createdAt', label: '导入时间' },
                {
                  key: 'digest',
                  label: '文件摘要',
                  format: (v) => v.slice(0, 20) + '…',
                },
              ]"
            ></DataGrid>
          </section>
          <section v-if="route === 'review'" class="panel">
            <div class="section-heading"><h2>历史核对</h2></div>
            <DataGrid
              :rows="
                finance.reconciliations.filter((r) => r.state !== 'PENDING')
              "
              :columns="[
                { key: 'ledgerId', label: '流水号' },
                { key: 'kind', label: '类型', state: true },
                { key: 'reason', label: '核对原因' },
                { key: 'state', label: '状态', state: true },
                { key: 'reviewerId', label: '复核账号', format: person },
              ]"
            ></DataGrid></section
        ></template>
        <template v-else-if="route === 'admin'"
          ><div class="tabs">
            <button
              v-for="(label, key) in adminNames"
              :key="key"
              :class="{ selected: adminTab === key }"
              @click="adminTab = key"
            >
              {{ label }}
            </button>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ adminNames[adminTab] }}</h2>
              <button
                v-if="
                  [
                    'accounts',
                    'roles',
                    'departments',
                    'dictionary',
                    'mappings',
                  ].includes(adminTab)
                "
                @click="adminForm(adminTab)"
              >
                新增
              </button>
            </div>
            <DataGrid :rows="admin[adminTab] || []" :columns="adminColumns"
              ><template v-if="adminTab !== 'audit'" #actions="{ row }"
                ><template
                  v-if="
                    !(
                      ['accounts', 'roles', 'departments'].includes(adminTab) &&
                      row.id === 1
                    )
                  "
                  ><button
                    class="link-button"
                    @click="adminForm(adminTab, row)"
                  >
                    修改</button
                  ><button
                    v-if="
                      [
                        'roles',
                        'departments',
                        'dictionary',
                        'mappings',
                      ].includes(adminTab)
                    "
                    class="link-button danger-text"
                    @click="
                      remove(
                        '/admin/' + adminTab + '/' + row.id,
                        row,
                        '删除未引用目录',
                      )
                    "
                  >
                    删除
                  </button></template
                ><span v-else class="muted">初始项受保护</span></template
              ></DataGrid
            >
          </section></template
        >
        <footer class="page-footer">
          <span>ResearchDesk · 知华科技 · 非商业源码学习版</span
          ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >官网与商业咨询 ↗</a
          >
        </footer>
      </main>
    </div>
  </div>
  <FormDialog
    v-if="dialog"
    :key="dialog.title"
    :spec="dialog"
    :busy="busy"
    :error="formError"
    @save="saveForm"
    @cancel="dialog = null"
  ></FormDialog>
</template>
