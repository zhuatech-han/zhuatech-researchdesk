// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.researchdesk;

import java.time.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 科研经费和履约协同应用入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootApplication
public class ResearchDeskApplication {
  /** 启动配置化实例。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(ResearchDeskApplication.class, args);
  }

  /** 可注入业务时钟，统一中国业务日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
  }
}
