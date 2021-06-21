package com.alibaba.nacos.example;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;

import java.util.Properties;

/**
 * @Description:
 * @Auther: create by cmj on 2021/6/21 16:32
 */
public class AppServiceNacos {

    public static void main(String[] args) throws Exception{
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "127.0.0.1:8848");
        properties.setProperty("namespace", "quickStart");
        NacosFactory.createConfigService(properties);
        Thread.sleep(1000*60*60);

    }
}
