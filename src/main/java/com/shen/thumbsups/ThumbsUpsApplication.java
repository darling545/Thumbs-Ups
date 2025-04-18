package com.shen.thumbsups;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.shen.thumbsups.mapper")
public class ThumbsUpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbsUpsApplication.class, args);
    }

}
