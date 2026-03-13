package com.medical.qc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.medical.qc")
@MapperScan("com.medical.qc.modules")
public class QcSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(QcSystemApplication.class, args);
    }

}

