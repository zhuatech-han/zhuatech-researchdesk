<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { money, states } from "./domain.js";
defineProps({
  rows: { type: Array, required: true },
  columns: { type: Array, required: true },
  empty: { type: String, default: "暂无记录" },
});
function cell(row, c) {
  const v = row[c.key];
  if (c.format) return c.format(v, row);
  if (c.money) return money(v);
  if (c.state) return states[v] || v;
  if (typeof v === "boolean") return v ? "是" : "否";
  return v === null || v === undefined || v === "" ? "—" : String(v);
}
</script>
<template>
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th v-for="c in columns" :key="c.key" :class="{ numeric: c.money }">
            {{ c.label }}
          </th>
          <th v-if="$slots.actions" class="action-col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(r, index) in rows"
          :key="r.id || `${r.projectId}-${r.fundId}-${index}`"
        >
          <td v-for="c in columns" :key="c.key" :class="{ numeric: c.money }">
            <span v-if="c.state" class="badge" :data-state="r[c.key]">{{
              cell(r, c)
            }}</span
            ><span v-else>{{ cell(r, c) }}</span>
          </td>
          <td v-if="$slots.actions" class="row-actions">
            <slot name="actions" :row="r"></slot>
          </td>
        </tr>
        <tr v-if="!rows.length">
          <td
            :colspan="columns.length + ($slots.actions ? 1 : 0)"
            class="empty"
          >
            {{ empty }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
