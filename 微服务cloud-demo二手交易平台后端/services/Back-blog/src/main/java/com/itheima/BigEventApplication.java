package com.itheima;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.itheima.mapper")
@SpringBootApplication
@EnableCaching
@EnableFeignClients
@EnableScheduling // 开启定时任务
@EnableDiscoveryClient
public class BigEventApplication{
    public static void main(String[] args) {
        SpringApplication.run(BigEventApplication.class, args);
    }
}