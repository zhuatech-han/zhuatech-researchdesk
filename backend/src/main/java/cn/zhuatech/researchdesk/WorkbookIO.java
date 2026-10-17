// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 有界CSV和XLSX接入；不计算公式、不跟随外部链接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class WorkbookIO {
  public static final List<String> FIELDS =
      List.of(
          "voucher",
          "lineNumber",
          "bookedOn",
          "amount",
          "projectCode",
          "categoryCode",
          "narrative",
          "reverseVoucher",
          "reverseLine");
  final ObjectMapper json;

  public WorkbookIO(ObjectMapper json) {
    this.json = json;
    ZipSecureFile.setMinInflateRatio(0.02);
    ZipSecureFile.setMaxEntrySize(8 * 1024 * 1024);
    ZipSecureFile.setMaxTextSize(8 * 1024 * 1024);
  }

  /** 映射只接受九个明确字段及不同的有界表头。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, String> mapping(String value) {
    try {
      var node = json.readTree(value);
      Rules.check(node.isObject() && node.size() == FIELDS.size(), "映射须包含九个标准字段");
      var out = new LinkedHashMap<String, String>();
      for (String key : FIELDS) {
        Rules.check(node.has(key) && node.get(key).isString(), "映射字段不完整");
        out.put(key, Rules.text(node.get(key).asText(), 80));
      }
      Rules.check(new HashSet<>(out.values()).size() == FIELDS.size(), "映射表头不能重复");
      return out;
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "表头映射JSON无效");
    }
  }

  /** 原子解析最多500行、30列及1MB源文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Map<String, String>> read(String filename, byte[] content, String mapping) {
    Rules.check(content.length > 0 && content.length <= 1024 * 1024, "财务文件须在1MB以内");
    List<List<String>> rows;
    try {
      if (filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
        String text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
        if (text.startsWith("\ufeff")) text = text.substring(1);
        rows = csv(text);
      } else if (filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
        rows = xlsx(content);
      } else throw new Problem(400, "只支持UTF-8 CSV或XLSX文件");
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "文件无法解析，请核对格式和大小");
    }
    Rules.check(rows.size() >= 2 && rows.size() <= 501, "须有表头和1至500条记录");
    var headers = rows.getFirst();
    Rules.check(
        headers.size() <= 30 && new HashSet<>(headers).size() == headers.size(), "表头重复或超过30列");
    var fields = mapping(mapping);
    for (String header : fields.values()) Rules.check(headers.contains(header), "缺少表头：" + header);
    var result = new ArrayList<Map<String, String>>();
    for (int i = 1; i < rows.size(); i++) {
      var cells = rows.get(i);
      Rules.check(cells.size() == headers.size(), "第" + (i + 1) + "行列数与表头不符");
      var row = new LinkedHashMap<String, String>();
      for (var f : fields.entrySet()) {
        String s = cells.get(headers.indexOf(f.getValue())).strip();
        Rules.check(s.length() <= 600, "第" + (i + 1) + "行单元格过长");
        row.put(f.getKey(), s);
      }
      result.add(row);
    }
    return result;
  }

  /** RFC4180风格逗号、转义引号与引号内换行，拒绝含糊拼接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<List<String>> csv(String text) {
    var rows = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    boolean quoted = false, closed = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else cell.append(c);
      } else if (c == '"') {
        Rules.check(cell.isEmpty() && !closed, "CSV引号位置无效");
        quoted = true;
      } else if (c == ',' || c == '\n' || c == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        closed = false;
        if (c != ',') {
          if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
          rows.add(List.copyOf(row));
          row.clear();
        }
      } else {
        Rules.check(!closed, "结束引号后只能为分隔符");
        cell.append(c);
      }
      Rules.check(cell.length() <= 1000 && row.size() <= 30 && rows.size() <= 501, "CSV超过大小限制");
    }
    Rules.check(!quoted, "CSV引号未闭合");
    if (!row.isEmpty() || !cell.isEmpty() || closed) {
      row.add(cell.toString());
      rows.add(List.copyOf(row));
    }
    return rows;
  }

  List<List<String>> xlsx(byte[] content) throws Exception {
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
      Rules.check(
          book.getNumberOfSheets() == 1
              && book.getExternalLinksTable().isEmpty()
              && !book.isMacroEnabled(),
          "XLSX须单工作表且不能有宏或外部链接");
      Rules.check(book.getPackage().getParts().size() <= 200, "XLSX包含过多内部对象");
      var sheet = book.getSheetAt(0);
      Rules.check(sheet.getLastRowNum() <= 500, "最多500条记录");
      var first = sheet.getRow(0);
      Rules.check(
          first != null && first.getLastCellNum() > 0 && first.getLastCellNum() <= 30, "表头无效");
      int columns = first.getLastCellNum();
      var result = new ArrayList<List<String>>();
      for (int i = 0; i <= sheet.getLastRowNum(); i++) {
        var r = sheet.getRow(i);
        Rules.check(r != null && r.getLastCellNum() <= columns, "XLSX空行或额外列");
        var values = new ArrayList<String>();
        for (int j = 0; j < columns; j++) {
          var c = r.getCell(j);
          if (c == null || c.getCellType() == CellType.BLANK) values.add("");
          else if (c.getCellType() == CellType.STRING) values.add(c.getStringCellValue());
          else if (c.getCellType() == CellType.NUMERIC) {
            values.add(
                DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(c.getNumericCellValue())
                        .stripTrailingZeros()
                        .toPlainString());
          } else throw new Problem(400, "XLSX不支持公式、错误或布尔单元格");
        }
        result.add(values);
      }
      return result;
    }
  }
}
