package com.itheima;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@MapperScan("com.itheima.mapper")
@SpringBootApplication
@EnableCaching
public class BigEventApplication{
    public static void main(String[] args) {
        SpringApplication.run(BigEventApplication.class, args);
    }

}