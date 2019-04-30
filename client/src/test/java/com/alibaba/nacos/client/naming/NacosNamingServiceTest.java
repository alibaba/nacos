package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;


public class NacosNamingServiceTest {

    private NamingService nameService;

    @Before
    public void before() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "11.160.165.126:8848");

        nameService = NacosFactory.createNamingService(properties);
    }

    @Test
    public void deleteService() {

    }

    @Test
    public void updateService() {

    }

    @Test
    public void registerInstance() throws NacosException {
        nameService.registerInstance("nacos-api", "127.0.0.1", 8009);
    }
}
