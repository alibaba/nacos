package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.MaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;
import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NacosMaintainServiceTest {

    private MaintainService maintainService;
    private NamingService namingService;

    @Before
    public void before() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");

        maintainService = NacosFactory.createMaintainService(properties);
    }

    @Test
    public void test1createService() {
        Service service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "this is a test metadata");
        service.setMetadata(metadata);

        ExpressionSelector selector = new ExpressionSelector();
        selector.setExpression("CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");

        try {
            maintainService.createService(service, new NoneSelector());
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void test2updateService() {
        Service service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "nacos-3-update");
        service.setMetadata(metadata);

        try {
            maintainService.updateService(service, new NoneSelector());
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void test3selectOneService() {
        try {
            Service service = maintainService.queryService("nacos-api");
            System.out.println("service : " + service.toString());
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

    @Test
    public void test4deleteService() {
        try {
            Assert.assertTrue(maintainService.deleteService("nacos-api"));
        } catch (NacosException e) {
            NAMING_LOGGER.error(e.getErrMsg());
        }
    }

}
