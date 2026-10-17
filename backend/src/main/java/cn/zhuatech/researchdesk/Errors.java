// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 统一参数、状态、并发和文件错误，隐藏SQL及堆栈。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestControllerAdvice
public class Errors {
  /** 领域错误保持实际HTTP状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @ExceptionHandler(Problem.class)
  ResponseEntity<?> business(Problem e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  /** 数据库约束与并发冲突不暴露表内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @ExceptionHandler({
    org.springframework.dao.DataIntegrityViolationException.class,
    org.springframework.dao.OptimisticLockingFailureException.class,
    jakarta.persistence.PersistenceException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(Map.of("message", "记录已更新、重复或仍被引用，请刷新核对"));
  }

  /** 拒绝超大文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> large(Exception e) {
    return ResponseEntity.status(413).body(Map.of("message", "文件过大"));
  }

  /** 类型、日期及反序列化错误使用400。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @ExceptionHandler({
    IllegalArgumentException.class,
    ClassCastException.class,
    ArithmeticException.class,
    java.time.DateTimeException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class
  })
  ResponseEntity<?> input(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("message", "参数格式或取值不正确"));
  }
}
