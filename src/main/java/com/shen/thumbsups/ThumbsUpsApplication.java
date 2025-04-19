package com.shen.thumbsups;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.shen.thumbsups.mapper")
@EnableScheduling
public class ThumbsUpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbsUpsApplication.class, args);
    }

}
