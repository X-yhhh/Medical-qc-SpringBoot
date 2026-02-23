package com.medical.qc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.medical.qc.mapper")
public class QcSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(QcSystemApplication.class, args);
    }

}
