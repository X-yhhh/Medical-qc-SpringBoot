package com.medical.qc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 后端应用启动入口。
 * 数据链路：Spring Boot 从此类启动，扫描 com.medical.qc 下的组件，并注册 modules 包中的 MyBatis Mapper。
 */
@SpringBootApplication(scanBasePackages = "com.medical.qc")
@MapperScan("com.medical.qc.modules")
public class QcSystemApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(QcSystemApplication.class, args);
    }

}

