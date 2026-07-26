package org.gtalent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 啟用定時任務台股即時分析工作台
public class Main {
    public static void main(String[] args) {
        AppRuntime.initializeSystemProperties();

        // 啟動 Spring Boot 應用
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);

        // 應用啟動完成後，執行一次年度備份
        ScheduledService scheduledService = context.getBean(ScheduledService.class);
        scheduledService.onStartup();

        AppRuntime.openBrowserIfEnabled(context.getEnvironment());
    }
}
