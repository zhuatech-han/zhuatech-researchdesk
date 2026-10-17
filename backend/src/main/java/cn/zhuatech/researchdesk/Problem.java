// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

/** 可安全显示给操作者的业务拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class Problem extends RuntimeException {
  final int status;

  public Problem(int status, String message) {
    super(message);
    this.status = status;
  }
}
