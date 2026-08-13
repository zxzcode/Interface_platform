package com.lzcer.interfaceplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.lzcer.interfaceplatform.mapper")
public class InterfacePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterfacePlatformApplication.class, args);
    }
}
