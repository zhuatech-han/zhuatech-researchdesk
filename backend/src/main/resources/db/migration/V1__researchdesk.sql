-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

-- 自有源码公开学习版；没有客户或财务业务种子。

create table write_guard(id bigint primary key);

insert into write_guard(id) values(1);

create table department (
 id bigint auto_increment primary key,
 version bigint not null,
 code varchar(60) not null,
 name varchar(120) not null,
 enabled boolean not null,
 unique (code)
);

create table access_role (
 id bigint auto_increment primary key,
 version bigint not null,
 name varchar(120) not null,
 scope varchar(20) not null,
 permissions varchar(1000) not null
);

create table account (
 id bigint auto_increment primary key,
 version bigint not null,
 username varchar(60) not null,
 display_name varchar(120) not null,
 password_hash varchar(100) not null,
 role_id bigint not null,
 department_id bigint not null,
 enabled boolean not null,
 unique (username)
);

create table dictionary_entry (
 id bigint auto_increment primary key,
 version bigint not null,
 group_code varchar(40) not null,
 code varchar(60) not null,
 label varchar(120) not null,
 enabled boolean not null,
 unique (group_code,code)
);

create table system_setting (
 id bigint auto_increment primary key,
 version bigint not null,
 code varchar(60) not null,
 label varchar(120) not null,
 parameter_value varchar(6000) not null,
 unique (code)
);

create table nav_menu (
 id bigint auto_increment primary key,
 version bigint not null,
 code varchar(40) not null,
 label varchar(120) not null,
 permission varchar(60) not null,
 position int not null,
 enabled boolean not null,
 unique (code)
);

create table audit_event (
 id bigint auto_increment primary key,
 version bigint not null,
 actor_id bigint not null,
 department_id bigint not null,
 project_id bigint,
 action varchar(80) not null,
 object_ref varchar(120) not null,
 note varchar(1600) not null,
 created_at timestamp(6) not null
);

create table research_project (
 id bigint auto_increment primary key,
 version bigint not null,
 code varchar(60) not null,
 title varchar(160) not null,
 department_id bigint not null,
 owner_id bigint not null,
 creator_id bigint not null,
 start_on date not null,
 end_on date not null,
 state varchar(20) not null,
 active_budget_id bigint,
 note varchar(1600) not null,
 unique (code)
);

create table project_member (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 account_id bigint not null,
 enabled boolean not null,
 unique (project_id,account_id)
);

create table funding_source (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 code varchar(60) not null,
 name varchar(160) not null,
 contract_reference varchar(120) not null,
 award_cents bigint not null,
 evidence_id bigint,
 unique (project_id,code)
);

create table budget_revision (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 revision_number int not null,
 state varchar(20) not null,
 reason varchar(1600) not null,
 creator_id bigint not null,
 reviewer_id bigint,
 approved_at timestamp(6),
 unique (project_id,revision_number)
);

create table budget_line (
 id bigint auto_increment primary key,
 version bigint not null,
 budget_id bigint not null,
 fund_id bigint not null,
 category_id bigint not null,
 amount_cents bigint not null,
 unique (budget_id,fund_id,category_id)
);

create table funding_receipt (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 fund_id bigint not null,
 reference varchar(120) not null,
 amount_cents bigint not null,
 booked_on date not null,
 evidence_id bigint not null,
 reversal_of bigint,
 state varchar(20) not null,
 creator_id bigint not null,
 reviewer_id bigint,
 note varchar(1600) not null,
 unique (reference)
);

create table import_mapping (
 id bigint auto_increment primary key,
 version bigint not null,
 department_id bigint not null,
 code varchar(60) not null,
 name varchar(120) not null,
 fields_json longtext not null,
 enabled boolean not null,
 unique (department_id,code)
);

create table import_batch (
 id bigint auto_increment primary key,
 version bigint not null,
 department_id bigint not null,
 creator_id bigint not null,
 mapping_id bigint not null,
 file_name varchar(160) not null,
 digest varchar(64) not null,
 mapping_snapshot longtext not null,
 row_count int not null,
 created_at timestamp(6) not null,
 unique (digest)
);

create table ledger_entry (
 id bigint auto_increment primary key,
 version bigint not null,
 batch_id bigint not null,
 department_id bigint not null,
 voucher varchar(120) not null,
 line_number int not null,
 booked_on date not null,
 amount_cents bigint not null,
 project_code varchar(60) not null,
 category_code varchar(60) not null,
 narrative varchar(600) not null,
 reverse_voucher varchar(120) not null,
 reverse_line int not null,
 project_id bigint,
 state varchar(20) not null,
 current_reconciliation_id bigint,
 unique (voucher,line_number)
);

create table reconciliation (
 id bigint auto_increment primary key,
 version bigint not null,
 ledger_id bigint not null,
 project_id bigint,
 kind varchar(20) not null,
 state varchar(20) not null,
 reason varchar(1600) not null,
 creator_id bigint not null,
 reviewer_id bigint,
 created_at timestamp(6) not null
);

create table allocation (
 id bigint auto_increment primary key,
 version bigint not null,
 reconciliation_id bigint not null,
 fund_id bigint not null,
 category_id bigint not null,
 amount_cents bigint not null
);

create table milestone (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 code varchar(60) not null,
 title varchar(160) not null,
 due_on date not null,
 required boolean not null,
 state varchar(20) not null,
 submitter_id bigint,
 reviewer_id bigint,
 frozen_evidence_ids varchar(2000) not null,
 round_number int not null,
 note varchar(1600) not null,
 unique (project_id,code)
);

create table evidence (
 retired boolean not null,
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 milestone_id bigint,
 file_name varchar(160) not null,
 content_type varchar(80) not null,
 size_bytes bigint not null,
 digest varchar(64) not null,
 content longblob not null,
 version_number int not null,
 creator_id bigint not null,
 created_at timestamp(6) not null,
 note varchar(600) not null
);

create table project_amendment (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 milestone_id bigint,
 old_date date not null,
 new_date date not null,
 state varchar(20) not null,
 reason varchar(1600) not null,
 creator_id bigint not null,
 reviewer_id bigint
);

create table closeout (
 id bigint auto_increment primary key,
 version bigint not null,
 project_id bigint not null,
 kind varchar(20) not null,
 state varchar(20) not null,
 creator_id bigint not null,
 reviewer_id bigint,
 reason varchar(1600) not null,
 residual_plan varchar(1600) not null,
 snapshot_json longtext not null,
 digest varchar(64) not null,
 created_at timestamp(6) not null
);

alter table account add foreign key (role_id) references access_role(id);

alter table account add foreign key (department_id) references department(id);

alter table research_project add foreign key (department_id) references department(id);

alter table research_project add foreign key (owner_id) references account(id);

alter table research_project add foreign key (creator_id) references account(id);

alter table project_member add foreign key (project_id) references research_project(id);

alter table project_member add foreign key (account_id) references account(id);

alter table funding_source add foreign key (project_id) references research_project(id);

alter table budget_revision add foreign key (project_id) references research_project(id);

alter table budget_revision add foreign key (creator_id) references account(id);

alter table budget_revision add foreign key (reviewer_id) references account(id);

alter table budget_line add foreign key (budget_id) references budget_revision(id);

alter table budget_line add foreign key (fund_id) references funding_source(id);

alter table budget_line add foreign key (category_id) references dictionary_entry(id);

alter table funding_receipt add foreign key (project_id) references research_project(id);

alter table funding_receipt add foreign key (fund_id) references funding_source(id);

alter table funding_receipt add foreign key (evidence_id) references evidence(id);

alter table funding_receipt add foreign key (creator_id) references account(id);

alter table funding_receipt add foreign key (reviewer_id) references account(id);

alter table funding_receipt add foreign key (reversal_of) references funding_receipt(id);

alter table import_mapping add foreign key (department_id) references department(id);

alter table import_batch add foreign key (department_id) references department(id);

alter table import_batch add foreign key (creator_id) references account(id);

alter table import_batch add foreign key (mapping_id) references import_mapping(id);

alter table ledger_entry add foreign key (batch_id) references import_batch(id);

alter table ledger_entry add foreign key (department_id) references department(id);

alter table ledger_entry add foreign key (project_id) references research_project(id);

alter table reconciliation add foreign key (ledger_id) references ledger_entry(id);

alter table reconciliation add foreign key (project_id) references research_project(id);

alter table reconciliation add foreign key (creator_id) references account(id);

alter table reconciliation add foreign key (reviewer_id) references account(id);

alter table allocation add foreign key (reconciliation_id) references reconciliation(id);

alter table allocation add foreign key (fund_id) references funding_source(id);

alter table allocation add foreign key (category_id) references dictionary_entry(id);

alter table milestone add foreign key (project_id) references research_project(id);

alter table evidence add foreign key (project_id) references research_project(id);

alter table evidence add foreign key (milestone_id) references milestone(id);

alter table evidence add foreign key (creator_id) references account(id);

alter table project_amendment add foreign key (project_id) references research_project(id);

alter table project_amendment add foreign key (milestone_id) references milestone(id);

alter table closeout add foreign key (project_id) references research_project(id);

alter table closeout add foreign key (creator_id) references account(id);

alter table closeout add foreign key (reviewer_id) references account(id);

create index ix_funding_receipt_scope on funding_receipt(project_id,state);

create index ix_ledger_entry_scope on ledger_entry(project_id,state);

create index ix_reconciliation_scope on reconciliation(project_id,state);

create index ix_evidence_scope on evidence(project_id,milestone_id);

create index ix_audit_event_scope on audit_event(project_id,created_at);

alter table research_project add foreign key (active_budget_id) references budget_revision(id);
alter table funding_source add foreign key (evidence_id) references evidence(id);
alter table ledger_entry add foreign key (current_reconciliation_id) references reconciliation(id);

-- 实际提交人与创建人分别保留，防止代编代提后自审。
alter table research_project add column submitter_id bigint null;
alter table research_project add constraint fk_project_submitter foreign key(submitter_id) references account(id);
alter table budget_revision add column submitter_id bigint null;
alter table budget_revision add constraint fk_budget_submitter foreign key(submitter_id) references account(id);
