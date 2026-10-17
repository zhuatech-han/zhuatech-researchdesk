// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 金额与实际导入解析边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RulesTest {
  @Test
  void pageActionPdfIsRejected() throws Exception {
    try (var doc = new org.apache.pdfbox.pdmodel.PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      var page = new org.apache.pdfbox.pdmodel.PDPage();
      doc.addPage(page);
      var actions = new org.apache.pdfbox.cos.COSDictionary();
      var js = new org.apache.pdfbox.cos.COSDictionary();
      js.setName(org.apache.pdfbox.cos.COSName.S, "JavaScript");
      js.setString(org.apache.pdfbox.cos.COSName.JS, "app.alert('TEST')");
      actions.setItem(org.apache.pdfbox.cos.COSName.getPDFName("O"), js);
      page.getCOSObject().setItem(org.apache.pdfbox.cos.COSName.AA, actions);
      doc.save(bytes);
      assertThrows(Problem.class, () -> Documents.validate("TEST-active.pdf", bytes.toByteArray()));
    }
  }

  @Test
  void exactMoney() {
    assertEquals(29, Rules.yuan("0.29"));
    assertEquals(-101, Rules.yuan("-1.01"));
    assertEquals(Rules.MAX, Rules.cents("10000000000000", false));
    for (String s :
        new String[] {"1.001", "1e2", "NaN", "0", "-0", " 1", "1,000", "100000000000.01"})
      assertThrows(RuntimeException.class, () -> Rules.yuan(s), s);
  }

  @Test
  void csvProtectsFormulaAndQuotes() {
    assertEquals("\"'=SUM(1)\"", Rules.csv("=SUM(1)"));
    assertEquals("\"a\"\"b\n\"", Rules.csv("a\"b\n"));
    assertEquals("\"'  @SUM(1)\"", Rules.csv("  @SUM(1)"));
  }

  @Test
  void rejectsMislabeledAndActiveFiles() {
    assertThrows(
        Problem.class, () -> Documents.validate("x.pdf", "hello".getBytes(StandardCharsets.UTF_8)));
    assertThrows(
        Problem.class,
        () -> Documents.validate("x.svg", "<svg/>".getBytes(StandardCharsets.UTF_8)));
  }

  static final String MAPPING =
      "{\"voucher\":\"凭证号\",\"lineNumber\":\"行号\",\"bookedOn\":\"日期\",\"amount\":\"金额\",\"projectCode\":\"项目\",\"categoryCode\":\"科目\",\"narrative\":\"摘要\",\"reverseVoucher\":\"原凭证\",\"reverseLine\":\"原行\"}";
  static final String HEADER = "凭证号,行号,日期,金额,项目,科目,摘要,原凭证,原行\r\n";

  @Test
  void csvQuotedNewlinesAndDuplicates() {
    var w = new WorkbookIO(JsonMapper.builder().build());
    var rows =
        w.read(
            "test.csv",
            (HEADER + "TEST-1,1,2026-01-01,0.29,TEST-P,MATERIAL,\"含逗号,和\n换行\",,\r\n")
                .getBytes(StandardCharsets.UTF_8),
            MAPPING);
    assertEquals("含逗号,和\n换行", rows.getFirst().get("narrative"));
    assertThrows(
        Problem.class,
        () -> w.read("test.csv", (HEADER + "a,1").getBytes(StandardCharsets.UTF_8), MAPPING));
    assertThrows(Problem.class, () -> w.mapping(MAPPING.replace("日期", "行号")));
  }

  @Test
  void xlsxFormulaAndMultiSheetRejected() throws Exception {
    var w = new WorkbookIO(JsonMapper.builder().build());
    try (var book = new XSSFWorkbook()) {
      var sheet = book.createSheet("TEST");
      var h = sheet.createRow(0);
      String[] headers = HEADER.strip().split(",");
      for (int i = 0; i < 9; i++) h.createCell(i).setCellValue(headers[i]);
      var row = sheet.createRow(1);
      for (int i = 0; i < 9; i++) row.createCell(i).setCellValue("");
      row.getCell(3).setCellFormula("1+1");
      try (var out = new ByteArrayOutputStream()) {
        book.write(out);
        assertThrows(Problem.class, () -> w.read("test.xlsx", out.toByteArray(), MAPPING));
      }
      row.getCell(3).removeFormula();
      row.getCell(3).setCellValue(2);
      book.createSheet("hidden");
      try (var out = new ByteArrayOutputStream()) {
        book.write(out);
        assertThrows(Problem.class, () -> w.read("test.xlsx", out.toByteArray(), MAPPING));
      }
    }
  }
}
