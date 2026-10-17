#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""全新实例的实际HTTP验收；只创建明确标记TEST的资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.request
import zipfile
from zoneinfo import ZoneInfo
from xml.sax.saxutils import escape

checks = 0


def check(ok, message):
    """保留实际断言，不输出凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global checks
    if not ok:
        raise AssertionError(message)
    checks += 1


class Client:
    """每个身份使用独立Cookie与CSRF。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

    def __init__(self, url):
        self.url = url.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def call(self, path, method="GET", body=None, expected=200, raw=False, upload=None):
        headers = {}
        if method != "GET":
            t = self.call("/api/auth/csrf")
            headers[t["header"]] = t["token"]
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        if upload:
            boundary = "TEST" + secrets.token_hex(16)
            parts = []
            for key, value in upload.get("fields", {}).items():
                parts.append(
                    f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
                )
            parts.append(
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{upload["name"]}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()
            )
            parts += [upload["data"], f"\r\n--{boundary}--\r\n".encode()]
            data = b"".join(parts)
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        req = urllib.request.Request(
            self.url + path, data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(req, timeout=60) as r:
                status, result = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, result = e.code, e.read()
        check(
            status == expected,
            f"{method} {path}: expected {expected}, got {status}: {result[:220]!r}",
        )
        if raw:
            return result
        return json.loads(result) if result else None

    def post(self, path, body, expected=200):
        return self.call(path, "POST", body, expected)

    def login(self, username, password):
        return self.post(
            "/api/auth/login", {"username": username, "password": password}
        )


def pdf(label="TEST research evidence", padding=0):
    """生成有真实交叉引用表的独立验收PDF。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    stream = (
        f"BT /F1 14 Tf 60 740 Td ({label}) Tj ET".encode()
        + b"\n% TEST evidence padding" * padding
    )
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length "
        + str(len(stream)).encode()
        + b" >>\nstream\n"
        + stream
        + b"\nendstream",
    ]
    out = b"%PDF-1.4\n"
    offsets = [0]
    for n, obj in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{n} 0 obj\n".encode() + obj + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode()
    out += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    return (
        out
        + f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    )


def xlsx(rows):
    """创建实际单工作表OOXML验收文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    sheet = '<worksheet xmlns="' + ns + '"><sheetData>'
    for n, row in enumerate(rows, 1):
        sheet += f'<row r="{n}">'
        for j, value in enumerate(row):
            sheet += f'<c r="{chr(65+j)}{n}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>'
        sheet += "</row>"
    sheet += "</sheetData></worksheet>"
    parts = {
        "[Content_Types].xml": '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
        "_rels/.rels": f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{rel}/officeDocument" Target="xl/workbook.xml"/></Relationships>',
        "xl/workbook.xml": f'<workbook xmlns="{ns}" xmlns:r="{rel}"><sheets><sheet name="TEST" sheetId="1" r:id="rId1"/></sheets></workbook>',
        "xl/_rels/workbook.xml.rels": f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{rel}/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
        "xl/worksheets/sheet1.xml": sheet,
    }
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in parts.items():
            archive.writestr(name, content.encode())
    return output.getvalue()


def main():
    """完整资金、成果和结题链路，私有状态仅供本机验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8172")
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--private-state", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    state_path = Path(args.private_state).resolve()
    env_path = Path(args.env_file)
    check(
        not state_path.is_relative_to(root) and not state_path.exists(),
        "Use a new private path outside source control",
    )
    check(
        env_path.is_file() and not env_path.stat().st_mode & 0o077,
        "Environment file must be private",
    )
    env = dict(
        line.split("=", 1)
        for line in env_path.read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    today = dt.datetime.now(ZoneInfo("Asia/Shanghai")).date()
    date = today.isoformat()
    pw = "Aa9" + secrets.token_hex(20)
    anonymous = Client(args.url)
    anonymous.call("/api/projects", expected=401)
    check(anonymous.call("/actuator/health")["status"] == "UP", "health")
    check(b"ResearchDesk" in anonymous.call("/", raw=True), "frontend served")
    admin = Client(args.url)
    profile = admin.login(env.get("ADMIN_USERNAME", "admin"), env["ADMIN_PASSWORD"])
    check("passwordHash" not in json.dumps(profile), "password hash hidden")
    check(
        admin.call("/api/projects")["total"] == 0
        and len(admin.call("/api/admin/accounts")) == 1,
        "Refuse to mutate a nonempty business instance",
    )
    dept = admin.post(
        "/api/admin/departments",
        {"code": "TEST-OTHER", "name": "TEST其他部门", "enabled": True},
    )
    users = {}
    clients = {}
    for username, role, department in [
        ("test_owner", 2, 1),
        ("test_reviewer", 3, 1),
        ("test_writer", 4, 1),
        ("test_checker", 5, 1),
        ("test_member", 2, 1),
        ("test_outside", 2, dept["id"]),
    ]:
        users[username] = admin.post(
            "/api/admin/accounts",
            {
                "username": username,
                "displayName": "TEST " + username,
                "password": pw,
                "roleId": role,
                "departmentId": department,
                "enabled": True,
            },
        )
        c = Client(args.url)
        c.login(username, pw)
        clients[username] = c
    owner, reviewer, writer, checker, member, outside = [
        clients[x]
        for x in [
            "test_owner",
            "test_reviewer",
            "test_writer",
            "test_checker",
            "test_member",
            "test_outside",
        ]
    ]
    project = owner.post(
        "/api/projects",
        {
            "code": "TEST-RD-001",
            "title": "TEST 科研经费与履约验收",
            "departmentId": 1,
            "ownerId": users["test_owner"]["id"],
            "startOn": (today - dt.timedelta(days=60)).isoformat(),
            "endOn": (today + dt.timedelta(days=90)).isoformat(),
            "note": "TEST 独立验收数据，无真实客户资料",
        },
    )
    pid = project["id"]
    base = f"/api/projects/{pid}"

    def detail():
        return admin.call(base)

    def find(resource, ident):
        return next(r for r in detail()[resource] if r["id"] == ident)

    def act(c, resource, row, action, expected=200):
        return c.post(
            f'/api/{resource}/{row["id"]}/actions',
            {"version": row["version"], "action": action, "note": "TEST验收处理记录"},
            expected,
        )

    owner.call(
        base,
        "PUT",
        {**project, "title": "TEST 过期写入", "version": project["version"] + 1},
        409,
    )
    proof = owner.call(
        base + "/files",
        "POST",
        upload={
            "name": "TEST-contract.pdf",
            "data": pdf(padding=128000),
            "fields": {"note": "TEST合同证明"},
        },
    )
    check(proof["sizeBytes"] > 65535, "large private BLOB persisted beyond 64KiB")
    check(
        hashlib.sha256(
            owner.call("/api/files/" + str(proof["id"]), raw=True)
        ).hexdigest()
        == proof["digest"],
        "file persisted exactly",
    )
    owner.call(
        base + "/files",
        "POST",
        expected=413,
        upload={"name": "TEST-oversize.pdf", "data": b"x" * (3 * 1024 * 1024 + 1)},
    )
    owner.call(
        base + "/files",
        "POST",
        expected=400,
        upload={"name": "TEST-fake.pdf", "data": b"not a PDF"},
    )
    funds = []
    for code, name, amount in [
        ("TEST-A", "TEST委托资金", "20000000"),
        ("TEST-B", "TEST配套资金", "10000000"),
    ]:
        funds.append(
            owner.post(
                base + "/funds",
                {
                    "code": code,
                    "name": name,
                    "contractReference": "TEST-CONTRACT-" + code,
                    "awardCents": amount,
                    "evidenceId": proof["id"],
                },
            )
        )
    milestone = owner.post(
        base + "/milestones",
        {
            "code": "TEST-M1",
            "title": "TEST阶段成果报告",
            "dueOn": (today + dt.timedelta(days=10)).isoformat(),
            "required": True,
            "note": "TEST静态PDF报告与验收",
        },
    )
    optional_milestone = owner.post(
        base + "/milestones",
        {
            "code": "TEST-M2",
            "title": "TEST可选交流纪要",
            "dueOn": (today + dt.timedelta(days=3)).isoformat(),
            "required": False,
            "note": "TEST选填交付，不阻断结题",
        },
    )
    budget = owner.post(base + "/budgets", {"reason": "TEST初始预算"})
    for f in funds:
        owner.post(
            f'/api/budgets/{budget["id"]}/lines',
            {"fundId": f["id"], "categoryId": 1, "amountCents": f["awardCents"]},
        )
    act(owner, "budgets", find("budgets", budget["id"]), "submit")
    act(reviewer, "budgets", find("budgets", budget["id"]), "approve")
    act(owner, "projects", detail()["project"], "submit")
    act(reviewer, "projects", detail()["project"], "approve")
    check(detail()["project"]["state"] == "ACTIVE", "project independently activated")
    receipts = []
    for i, f in enumerate(funds):
        r = writer.post(
            base + "/receipts",
            {
                "fundId": f["id"],
                "reference": "TEST-RECEIPT-" + str(i),
                "amountCents": str([10000000, 5000000][i]),
                "bookedOn": date,
                "evidenceId": proof["id"],
                "note": "TEST核实实际到账",
            },
        )
        receipts.append(r)
        act(checker, "receipts", find("receipts", r["id"]), "approve")
    outside.call(base, expected=404)
    outside.call("/api/files/" + str(proof["id"]), expected=404)
    outside.call(base + "/export", expected=404)
    check(outside.call("/api/reports")["rows"] == [], "report scoped")
    owner.call("/api/admin/accounts", expected=403)
    owner.call("/api/finance", expected=403)
    directory_role = admin.post(
        "/api/admin/roles",
        {"name": "TEST目录管理员", "scope": "ALL", "permissions": ["admin"]},
    )
    admin.post(
        "/api/admin/accounts",
        {
            "username": "test_directory",
            "displayName": "TEST目录管理",
            "roleId": directory_role["id"],
            "departmentId": 1,
            "enabled": True,
            "password": pw,
        },
    )
    directory = Client(args.url)
    directory.login("test_directory", pw)
    check(
        directory.call("/api/references")["projects"] == [],
        "directory-only administrator cannot read projects",
    )
    directory.call("/api/admin/accounts")
    directory.call(base, expected=403)
    admin.post(
        "/api/admin/roles",
        {
            "name": "TEST缺失项目权限",
            "scope": "DEPARTMENT",
            "permissions": ["budget.write"],
        },
        400,
    )
    member.call(base, expected=404)
    owner.post(
        base + "/members", {"accountId": users["test_member"]["id"], "enabled": True}
    )
    member.call(base)
    owner.post(
        base + "/members", {"accountId": users["test_member"]["id"], "enabled": False}
    )
    member.call(base, expected=404)
    header = "凭证号,行号,日期,金额,项目编号,科目代码,摘要,原凭证号,原行号\r\n"

    def import_rows(rows, expected=200):
        return writer.call(
            "/api/finance/import",
            "POST",
            expected=expected,
            upload={
                "name": "TEST-finance.csv",
                "data": (header + "".join(rows)).encode(),
                "fields": {"mappingId": 1},
            },
        )

    def line(voucher, amount, original=""):
        return f'{voucher},1,{date},{amount},TEST-RD-001,MATERIAL,TEST科研支出,{original},{1 if original else ""}\r\n'

    def entry(voucher):
        return next(
            r for r in writer.call("/api/finance")["entries"] if r["voucher"] == voucher
        )

    import_rows([line("TEST-EXP-1", "120000.00")])
    import_rows([line("TEST-EXP-1", "120000.00")], 400)
    import_rows([line("TEST-ATOMIC", "1.00"), line("TEST-EXP-1", "2.00")], 400)
    check(
        not any(
            r["voucher"] == "TEST-ATOMIC"
            for r in writer.call("/api/finance")["entries"]
        ),
        "atomic import rollback",
    )
    import_rows([line("TEST-PRECISION", "1.001")], 400)

    def allocate(voucher, a, b, client=writer, expected=200):
        l = entry(voucher)
        return client.post(
            f'/api/finance/entries/{l["id"]}/submit',
            {
                "version": l["version"],
                "kind": "ALLOCATE",
                "projectId": pid,
                "reason": "TEST按资金来源核对",
                "lines": [
                    {"fundId": funds[0]["id"], "categoryId": 1, "amountCents": a},
                    {"fundId": funds[1]["id"], "categoryId": 1, "amountCents": b},
                ],
            },
            expected,
        )

    rec = allocate("TEST-EXP-1", "8000000", "4000000")
    summary = detail()["summary"]
    check(
        sum(int(s["heldCents"]) for s in summary) == 12000000,
        "pending exact reservation",
    )
    import_rows([line("TEST-OVER", "50000.00")])
    allocate("TEST-OVER", "4000000", "1000000", expected=400)
    next_budget = owner.post(base + "/budgets", {"reason": "TEST调剂不能挤占待审"})
    bl = next(
        l
        for l in detail()["budgetLines"]
        if l["budgetId"] == next_budget["id"] and l["fundId"] == funds[0]["id"]
    )
    owner.call(
        f'/api/budgets/{next_budget["id"]}/lines/{bl["id"]}',
        "PUT",
        {**bl, "amountCents": "7000000"},
    )
    owner.post(
        f'/api/budgets/{next_budget["id"]}/lines',
        {"fundId": funds[0]["id"], "categoryId": 2, "amountCents": "13000000"},
    )
    act(owner, "budgets", find("budgets", next_budget["id"]), "submit", 400)
    act(owner, "budgets", find("budgets", next_budget["id"]), "withdraw")
    checker.post(
        f'/api/finance/reconciliations/{rec["id"]}/review',
        {"version": rec["version"], "action": "approve", "note": "TEST核实来源与科目"},
    )
    check(
        sum(int(s["spentCents"]) for s in detail()["summary"]) == 12000000,
        "approved spending posted",
    )
    import_rows([line("TEST-REV", "-120000.00", "TEST-EXP-1")])
    l = entry("TEST-REV")
    rr = writer.post(
        f'/api/finance/entries/{l["id"]}/submit',
        {
            "version": l["version"],
            "kind": "REVERSE",
            "projectId": pid,
            "reason": "TEST完整冲正",
        },
    )
    checker.post(
        f'/api/finance/reconciliations/{rr["id"]}/review',
        {"version": rr["version"], "action": "approve", "note": "TEST核实完整反向分摊"},
    )
    check(
        sum(int(s["spentCents"]) for s in detail()["summary"]) == 0, "reversal net zero"
    )
    import_rows([line("TEST-CORRECT", "10000.29")])
    r = allocate("TEST-CORRECT", "400010", "600019", client=admin)
    admin.post(
        f'/api/finance/reconciliations/{r["id"]}/review',
        {"version": r["version"], "action": "approve", "note": "TEST禁止自己复核"},
        400,
    )
    checker.post(
        f'/api/finance/reconciliations/{r["id"]}/review',
        {"version": r["version"], "action": "approve", "note": "TEST独立核实精确金额"},
    )
    effective = owner.post(
        base + "/budgets", {"reason": "TEST执行期预算调剂，保留已支出额度"}
    )
    current_line = next(
        line
        for line in detail()["budgetLines"]
        if line["budgetId"] == effective["id"] and line["fundId"] == funds[0]["id"]
    )
    owner.call(
        f'/api/budgets/{effective["id"]}/lines/{current_line["id"]}',
        "PUT",
        {**current_line, "amountCents": "19000000"},
    )
    owner.post(
        f'/api/budgets/{effective["id"]}/lines',
        {"fundId": funds[0]["id"], "categoryId": 2, "amountCents": "1000000"},
    )
    act(owner, "budgets", find("budgets", effective["id"]), "submit")
    act(reviewer, "budgets", find("budgets", effective["id"]), "approve")
    check(
        detail()["project"]["activeBudgetId"] == effective["id"]
        and sum(int(row["spentCents"]) for row in detail()["summary"]) == 1000029,
        "approved budget revision preserves posted spending",
    )
    l = entry("TEST-OVER")
    excluded = writer.post(
        f'/api/finance/entries/{l["id"]}/submit',
        {
            "version": l["version"],
            "kind": "EXCLUDE",
            "reason": "TEST非科研业务凭证，保留原始记录",
        },
    )
    checker.post(
        f'/api/finance/reconciliations/{excluded["id"]}/review',
        {
            "version": excluded["version"],
            "action": "approve",
            "note": "TEST核实排除理由",
        },
    )
    excel = writer.call(
        "/api/finance/import",
        "POST",
        upload={
            "name": "TEST-ledger.xlsx",
            "data": xlsx(
                [
                    header.strip().split(","),
                    [
                        "TEST-XLSX",
                        1,
                        date,
                        "0.29",
                        "TEST-RD-001",
                        "MATERIAL",
                        "TEST实际XLSX核对",
                        "",
                        "",
                    ],
                ]
            ),
            "fields": {"mappingId": 1},
        },
    )
    check(
        excel["rowCount"] == 1 and entry("TEST-XLSX")["amountCents"] == "29",
        "real XLSX persisted exact cents",
    )
    xl = entry("TEST-XLSX")
    xr = writer.post(
        f'/api/finance/entries/{xl["id"]}/submit',
        {
            "version": xl["version"],
            "kind": "EXCLUDE",
            "reason": "TEST单工作表接入验收，不计业务支出",
        },
    )
    checker.post(
        f'/api/finance/reconciliations/{xr["id"]}/review',
        {
            "version": xr["version"],
            "action": "approve",
            "note": "TEST核实文件接入与排除",
        },
    )
    writer.post(
        base + "/receipts",
        {
            "fundId": funds[0]["id"],
            "reference": "TEST-RECEIPT-REV",
            "amountCents": receipts[0]["amountCents"],
            "bookedOn": date,
            "evidenceId": proof["id"],
            "note": "TEST不能透支冲正",
            "reversalOf": receipts[0]["id"],
        },
    )
    pending = next(
        r for r in detail()["receipts"] if r["reference"] == "TEST-RECEIPT-REV"
    )
    act(checker, "receipts", pending, "approve", 400)
    act(checker, "receipts", find("receipts", pending["id"]), "reject")
    amendment = owner.post(
        base + "/amendments",
        {
            "milestoneId": milestone["id"],
            "newDate": (today + dt.timedelta(days=14)).isoformat(),
            "reason": "TEST里程碑日期调整",
        },
    )
    act(reviewer, "amendments", find("amendments", amendment["id"]), "approve")
    for n in (1, 2):
        owner.call(
            base + "/files",
            "POST",
            upload={
                "name": "TEST-report.pdf",
                "data": pdf("TEST milestone version " + str(n)),
                "fields": {
                    "milestoneId": milestone["id"],
                    "note": "TEST成果版本 " + str(n),
                },
            },
        )
    act(owner, "milestones", find("milestones", milestone["id"]), "submit")
    act(reviewer, "milestones", find("milestones", milestone["id"]), "accept")
    check(
        len([f for f in detail()["files"] if f["milestoneId"] == milestone["id"]]) == 2,
        "historical file versions retained",
    )
    check(not detail()["closeBlockers"], "no outstanding blockers")
    close = owner.post(
        base + "/closeouts",
        {
            "kind": "CLOSE",
            "reason": "TEST全部成果和财务已核对",
            "residualPlan": "TEST结余资金按合同另行办理，不自动划款",
        },
    )
    writer.post(
        base + "/receipts",
        {
            "fundId": funds[0]["id"],
            "reference": "TEST-AFTER-CLOSE",
            "amountCents": "1",
            "bookedOn": date,
            "evidenceId": proof["id"],
            "note": "TEST冻结禁止",
        },
        400,
    )
    act(reviewer, "closeouts", find("closeouts", close["id"]), "approve")
    bundle = owner.call("/api/closeouts/" + str(close["id"]) + "/bundle", raw=True)
    with zipfile.ZipFile(io.BytesIO(bundle)) as z:
        snapshot = z.read("snapshot.json")
        check(
            hashlib.sha256(snapshot).hexdigest() == close["digest"],
            "snapshot hash exact",
        )
        data = json.loads(snapshot)
        approval = json.loads(z.read("approval.json"))
        check(
            approval["closeout"]["reviewerId"] == users["test_reviewer"]["id"]
            and len(approval["approvalEvents"]) == 1,
            "actual closeout reviewer and time included",
        )
        check(
            len(data["budgetRevisions"]) == 3 and len(data["amendments"]) == 1,
            "budget and schedule history frozen",
        )
        check(len(data["files"]) == 2, "contract and submitted latest report frozen")
        check(
            any(n.startswith("files/") for n in z.namelist())
            and "allocations.csv" in z.namelist(),
            "actual financial and file bundle",
        )
    reopen = owner.post(
        base + "/closeouts", {"kind": "REOPEN", "reason": "TEST重开纠错"}
    )
    act(reviewer, "closeouts", find("closeouts", reopen["id"]), "approve")
    act(owner, "milestones", find("milestones", milestone["id"]), "revise")
    owner.call(
        base + "/files",
        "POST",
        upload={
            "name": "TEST-report.pdf",
            "data": pdf("TEST milestone version 3"),
            "fields": {"milestoneId": milestone["id"], "note": "TEST重开后补充"},
        },
    )
    check(
        bundle
        == owner.call("/api/closeouts/" + str(close["id"]) + "/bundle", raw=True),
        "old approved bundle immutable after reopen",
    )
    check(
        "TEST-A" in owner.call(base + "/export", raw=True).decode("utf-8-sig"),
        "financial source code exported",
    )
    u = users["test_member"]
    admin.call(
        "/api/admin/accounts/" + str(u["id"]),
        "PUT",
        {**u, "enabled": False, "password": ""},
    )
    member.call("/api/auth/me", expected=401)
    check(detail()["project"]["state"] == "ACTIVE", "project reopened independently")
    due = owner.call("/api/reports")["due"]
    check(
        {row["id"] for row in due} == {milestone["id"], optional_milestone["id"]}
        and [row["dueOn"] for row in due] == sorted(row["dueOn"] for row in due),
        "distinct pending milestones are ordered by due date",
    )
    fd = os.open(state_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(
            {
                "projectId": pid,
                "closeoutId": close["id"],
                "proofId": proof["id"],
                "proofDigest": proof["digest"],
                "bundleDigest": hashlib.sha256(bundle).hexdigest(),
                "testPassword": pw,
                "users": {
                    name: {"id": u["id"], "username": name} for name, u in users.items()
                },
                "assertions": checks,
            },
            f,
            ensure_ascii=False,
            indent=2,
        )
    print(
        json.dumps(
            {
                "result": "passed",
                "assertions": checks,
                "project": "TEST-RD-001",
                "scope": "fresh MySQL HTTP, exact money, split, reversal, evidence and immutable closeout",
            }
        )
    )


if __name__ == "__main__":
    main()
