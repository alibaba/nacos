package com.alibaba.nacos.config.server.repository;

import java.util.Date;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfo;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.utils.MD5;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class ConfigInfoRepositoryTest {

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    @Test
    public void findByDataIdAndGroupIdAndTenantIdTest() {
        List<ConfigInfo> list = configInfoRepository.findByDataIdAndGroupIdAndTenantId("userService", "DEFAULT_GROUP", "zhangsan");
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void findAllTest() {
        BooleanBuilder builder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        builder.and(qConfigInfo.dataId.eq("userService"));
        builder.and(qConfigInfo.appName.eq("userService"));
        Page<ConfigInfo> page = configInfoRepository.findAll(builder, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("gmtCreate"))));
        Assert.assertEquals(page.get().count(), 1);
    }


    @Test
    public void deleteTest() {
        Iterable<ConfigInfo> iterable = configInfoRepository.findAll();
        iterable.forEach(s -> configInfoRepository.delete(s));
    }

    @Test
    public void saveTest() {
        configInfoRepository.save(buildConfigInfo());
    }

    private ConfigInfo buildConfigInfo() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("userService");
        configInfo.setGroupId("DEFAULT_GROUP");
        configInfo.setContent("logEnable=true");
        configInfo.setMd5(MD5.getInstance().getMD5String("logEnable=true"));
        configInfo.setGmtCreate(new Date());
        configInfo.setGmtModified(new Date());
        configInfo.setSrcUser("zhangsan");
        configInfo.setSrcIp("127.0.0.1");
        configInfo.setAppName("userService");
        configInfo.setTenantId("zhangsan");
        configInfo.setCDesc("用户系统");
        configInfo.setCUse("");
        configInfo.setEffect("");
        configInfo.setType("properties");
        configInfo.setCSchema("");
        JSON.toJSONString(configInfo);
        return configInfo;
    }


}
