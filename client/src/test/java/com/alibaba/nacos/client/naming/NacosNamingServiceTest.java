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
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");

        nameService = NacosFactory.createNamingService(properties);
    }

    @Test
    public void createService() {
        Service service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "test-1");
        service.setMetadata(metadata);

        ExpressionSelector selector = new ExpressionSelector();
        selector.setExpression("CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");

        try {
            nameService.createService(service, new NoneSelector());
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void deleteService() {
        try {
            Assert.assertTrue(nameService.deleteService("nacos-api"));
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void updateService() {
        Service service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "nacos-3-update");
        service.setMetadata(metadata);

        try {
            nameService.updateService(service);
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void registerInstance() throws NacosException {
        nameService.registerInstance("nacos-api", "127.0.0.1", 8009);
    }
}
