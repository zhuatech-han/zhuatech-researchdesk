// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { cents, money, yuan } from "./domain.js";
test("money stays exact across maximum and cents", () => {
  assert.equal(cents("0.29"), "29");
  assert.equal(cents("100000000000.00"), "10000000000000");
  assert.equal(money("10000000000000"), "100,000,000,000.00");
  assert.equal(money("-101"), "-1.01");
  assert.equal(yuan("-29"), "-0.29");
});
test("money rejects rounding, scientific notation and overflow", () => {
  for (const s of ["1.001", "1e2", "NaN", "0", "-1", "100000000000.01"])
    assert.throws(() => cents(s));
});
