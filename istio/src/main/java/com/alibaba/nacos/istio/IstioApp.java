package com.alibaba.nacos.istio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author nkorange
 * @since 1.1.4
 */
@EnableScheduling
@SpringBootApplication
public class IstioApp {

    public static void main(String[] args) {
        SpringApplication.run(IstioApp.class, args);
    }
}
