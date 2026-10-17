// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** 精确整数分、强密码、日期、版本和规范化凭证键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Rules {
  public static final long MAX = 10000000000000L;

  /** 验证领域约束，失败返回400。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void check(boolean condition, String message) {
    if (!condition) throw new Problem(400, message);
  }

  /** 必填有界纯文本；不允许控制字符。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String text(Object value, int max) {
    String s = Objects.toString(value, "").strip();
    check(
        !s.isEmpty()
            && s.length() <= max
            && !s.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'),
        "文本为空、过长或含无效字符");
    return s;
  }

  /** 读取允许为空的有界文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(Object value, int max) {
    return value == null || value.toString().isBlank() ? "" : text(value, max);
  }

  /** 解析显式记录编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long id(Object value) {
    return Long.parseLong(Objects.toString(value, ""));
  }

  /** 解析可选关联编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Long nullableId(Object value) {
    return value == null || value.toString().isBlank() ? null : id(value);
  }

  /** 解析布尔配置值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "true".equals(value);
  }

  /** 业务日期必须显式采用ISO格式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static LocalDate date(Object value) {
    return LocalDate.parse(Objects.toString(value, ""));
  }

  /** 规范化稳定业务编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String code(Object value) {
    String s = text(value, 60).toUpperCase(Locale.ROOT);
    check(s.matches("[A-Z0-9][A-Z0-9_.-]{0,59}"), "编号仅允许字母、数字、横线、下划线和点");
    return s;
  }

  /** 规范化凭证键并排除控制符。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String voucher(Object value) {
    String s = text(value, 120).toUpperCase(Locale.ROOT);
    check(!s.contains("\n") && !s.contains("\t"), "凭证编号无效");
    return s;
  }

  /** API金额只接受整数分，拒绝浮点和超界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long cents(Object value, boolean signed) {
    String s = Objects.toString(value, "");
    check(s.matches(signed ? "-?[0-9]+" : "[0-9]+"), "金额必须为整数分");
    long n = Long.parseLong(s);
    check(n != 0 && n >= -MAX && n <= MAX && (signed || n > 0), "金额超出范围");
    return n;
  }

  /** 财务文件以元表示，只允许两位小数，不做隐式舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long yuan(String value) {
    check(value.matches("-?[0-9]+(\\.[0-9]{1,2})?"), "财务金额须为最多两位小数的人民币元");
    return cents(new BigDecimal(value).movePointRight(2).toBigIntegerExact().toString(), true);
  }

  /** 金额累加时检测整数溢出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long add(long a, long b) {
    return Math.addExact(a, b);
  }

  /** 将整数分精确格式化为元。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String money(long value) {
    return BigDecimal.valueOf(value, 2).toPlainString();
  }

  /** 当前版本与客户端一致才允许写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(Models.Row r, Object version) {
    if (r.version != id(version)) throw new Problem(409, "记录已更新，请刷新后重试");
  }

  /** 校验状态机允许的当前状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void state(String actual, String... allowed) {
    check(Arrays.asList(allowed).contains(actual), "当前状态不允许此操作");
  }

  /** 经办人不能批准自己的记录，包括管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void separate(Long creator, Long reviewer) {
    check(!Objects.equals(creator, reviewer), "须由另一名具备权限的人员复核");
  }

  /** 口令限制遵循BCrypt实际字节上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void password(String s) {
    int len = s.getBytes(StandardCharsets.UTF_8).length;
    check(
        len >= 12
            && len <= 72
            && s.matches("(?s).*[a-z].*")
            && s.matches("(?s).*[A-Z].*")
            && s.matches("(?s).*[0-9].*")
            && !s.chars().anyMatch(Character::isISOControl),
        "密码须12至72字节，包含大小写字母和数字");
  }

  /** 计算文件或快照SHA256摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** CSV单元格防止公式注入，不改变业务数据库值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object value) {
    String s = Objects.toString(value, "");
    if (s.stripLeading().matches("(?s)^[=+\\-@].*")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
