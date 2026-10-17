// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import static cn.zhuatech.researchdesk.Models.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 私有文件版本、真实格式解析及逐次范围授权下载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Documents {
  final Store db;
  final Access access;
  final Clock clock;

  public Documents(Store db, Access access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    ImageIO.setUseCache(false);
  }

  /** 生成安全展示及下载文件名，原始路径不进入响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String filename(String name) {
    String s = Rules.text(name, 160).replaceAll("[^\\p{L}\\p{N}_. -]", "_");
    Rules.check(!s.equals(".") && !s.equals(".."), "文件名无效");
    return s;
  }

  /** PDF须可解析、未加密、无表单/嵌入文件；图片先校验像素再解码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String validate(String name, byte[] data) {
    Rules.check(data.length > 0 && data.length <= 3 * 1024 * 1024, "文件须在3MB以内");
    String lower = name.toLowerCase(Locale.ROOT);
    try {
      if (lower.endsWith(".pdf")) {
        Rules.check(
            data.length > 8 && new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
            "PDF文件签名无效");
        try (var doc = Loader.loadPDF(data)) {
          Rules.check(
              !doc.isEncrypted() && doc.getNumberOfPages() > 0 && doc.getNumberOfPages() <= 200,
              "仅支持200页以内未加密PDF");
          validatePdfObjects(doc.getDocumentCatalog().getCOSObject());
          var c = doc.getDocumentCatalog();
          Rules.check(
              c.getAcroForm() == null
                  && c.getOpenAction() == null
                  && (c.getNames() == null
                      || (c.getNames().getJavaScript() == null
                          && c.getNames().getEmbeddedFiles() == null)),
              "仅支持无活动内容的静态PDF");
        }
        return "application/pdf";
      }
      Rules.check(
          lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"),
          "只支持静态PDF、PNG和JPEG");
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
        var readers = ImageIO.getImageReaders(stream);
        Rules.check(readers.hasNext(), "图片无法解析");
        var reader = readers.next();
        try {
          reader.setInput(stream);
          long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
          Rules.check(pixels > 0 && pixels <= 20000000, "图片须不超过2000万像素");
          String type = reader.getFormatName().toLowerCase(Locale.ROOT);
          Rules.check(
              type.equals("png")
                  ? lower.endsWith(".png")
                  : type.equals("jpeg") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")),
              "图片格式与扩展名不一致");
          Rules.check(reader.read(0) != null, "图片数据无效");
          return type.equals("png") ? "image/png" : "image/jpeg";
        } finally {
          reader.dispose();
        }
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "文件损坏、含不支持内容或无法解析");
    }
  }

  /** 二进制与元数据在同一MySQL事务中保存，每个名称递增版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> upload(
      Long projectId, Long milestoneId, String name, byte[] data, String note) {
    db.writeLock();
    var u = access.current();
    var p = access.project(projectId);
    Rules.state(p.state, "DRAFT", "ACTIVE");
    if (milestoneId != null) {
      access.require("delivery.write");
      var m = db.get(Milestone.class, milestoneId);
      Rules.check(m.projectId.equals(p.id), "里程碑跨项目");
      Rules.state(m.state, "OPEN", "CHANGES");
    } else if (!access.has("project.write") && !access.has("finance.write"))
      throw new Problem(403, "没有文件上传权限");
    String clean = filename(name), type = validate(clean, data);
    var existing = metadata(projectId);
    Rules.check(existing.size() < 100, "每项目最多100个文件版本");
    long bytes = existing.stream().mapToLong(m -> ((Number) m.get("sizeBytes")).longValue()).sum();
    int cap =
        Integer.parseInt(
            db.query(Setting.class, "from Setting where code='max_project_mb'")
                .getFirst()
                .parameterValue);
    Rules.check(bytes + data.length <= (long) cap * 1024 * 1024, "项目文件容量已满");
    int version =
        existing.stream()
                .filter(
                    m ->
                        Objects.equals(m.get("milestoneId"), milestoneId)
                            && m.get("fileName").equals(clean))
                .mapToInt(m -> ((Number) m.get("versionNumber")).intValue())
                .max()
                .orElse(0)
            + 1;
    var e = new Evidence();
    e.projectId = p.id;
    e.milestoneId = milestoneId;
    e.fileName = clean;
    e.contentType = type;
    e.sizeBytes = data.length;
    e.digest = Rules.digest(data);
    e.content = data;
    e.versionNumber = version;
    e.creatorId = u.id;
    e.createdAt = clock.instant();
    e.note = Rules.optional(note, 600);
    db.save(e);
    access.audit("EVIDENCE_UPLOAD", e.id, p.id, "版本 " + version);
    db.flush();
    return metadata(p.id).stream().filter(m -> m.get("id").equals(e.id)).findFirst().orElseThrow();
  }

  /** 有界遍历PDF对象，拒绝活动动作和嵌入文件；不替代杀毒服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static void validatePdfObjects(COSBase root) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
    var pending = new ArrayDeque<COSBase>();
    pending.push(root);
    var keys =
        Set.of("JS", "AA", "OpenAction", "EmbeddedFiles", "AF", "AcroForm", "RichMediaContent");
    var actions =
        Set.of(
            "JavaScript",
            "Launch",
            "SubmitForm",
            "ImportData",
            "GoToR",
            "GoToE",
            "Rendition",
            "Movie",
            "Sound");
    while (!pending.isEmpty()) {
      COSBase value = pending.pop();
      if (!seen.add(value)) continue;
      Rules.check(seen.size() <= 100000, "PDF对象数量超限");
      if (value instanceof COSObject object) {
        if (object.getObject() != null) pending.push(object.getObject());
      } else if (value instanceof COSDictionary dict) {
        for (var key : dict.keySet())
          Rules.check(!keys.contains(key.getName()), "PDF含活动动作、表单或嵌入文件");
        var action = dict.getDictionaryObject(COSName.S);
        Rules.check(!(action instanceof COSName n) || !actions.contains(n.getName()), "PDF动作不受支持");
        for (var child : dict.getValues()) if (child != null) pending.push(child);
      } else if (value instanceof COSArray array) {
        for (var child : array) if (child != null) pending.push(child);
      }
    }
  }

  /** 文件列表投影不把私有二进制加载到列表或JSON。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> metadata(Long projectId) {
    access.project(projectId);
    var rows =
        db.query(
            Object[].class,
            "select e.id,e.version,e.projectId,e.milestoneId,e.fileName,e.contentType,e.sizeBytes,e.digest,e.versionNumber,e.creatorId,e.createdAt,e.note,e.retired from Evidence e where e.projectId=?1 order by e.id",
            projectId);
    String[] keys = {
      "id",
      "version",
      "projectId",
      "milestoneId",
      "fileName",
      "contentType",
      "sizeBytes",
      "digest",
      "versionNumber",
      "creatorId",
      "createdAt",
      "note",
      "retired"
    };
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> m = new LinkedHashMap<>();
              for (int i = 0; i < keys.length; i++) m.put(keys[i], row[i]);
              return m;
            })
        .toList();
  }

  /** 成果当前版本按名称取最后一版，撤回不会令旧版重新成为当前版。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Long> latest(Long projectId, Long milestoneId) {
    var rows = metadata(projectId);
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    for (var e : rows)
      if (Objects.equals(e.get("milestoneId"), milestoneId))
        files.put(e.get("fileName").toString(), e);
    return files.values().stream()
        .filter(m -> !Boolean.TRUE.equals(m.get("retired")))
        .map(m -> (Long) m.get("id"))
        .toList();
  }

  /** 下载保留历史版本，每次检查当前账号与项目归属。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Evidence file(Long id) {
    var e = db.get(Evidence.class, id);
    access.project(e.projectId);
    return e;
  }

  /** 合同与到账已引用文件不能撤回；成果撤回保留原字节及审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void retire(Long id, Object version) {
    db.writeLock();
    var e = file(id);
    var p = access.project(e.projectId);
    Rules.state(p.state, "DRAFT", "ACTIVE");
    Rules.version(e, version);
    if (e.milestoneId != null) {
      access.require("delivery.write");
      Rules.state(db.get(Milestone.class, e.milestoneId).state, "OPEN", "CHANGES");
    } else if (!access.has("project.write") && !access.has("finance.write"))
      throw new Problem(403, "无权限");
    Rules.check(
        db.query(Fund.class, "from Fund where evidenceId=?1", id).isEmpty()
            && db.query(Receipt.class, "from Receipt where evidenceId=?1", id).isEmpty(),
        "文件已被合同或到账凭据引用");
    e.retired = true;
    access.audit("EVIDENCE_RETIRE", id, p.id, "");
  }
}
