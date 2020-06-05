package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfo;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.console.BaseTest;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoRepositoryTest extends BaseTest {

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    private ConfigInfo configInfo;

    @Before
    public void before() {
        String data = readClassPath("test-data/config_info.json");
        configInfo = JacksonUtils.toObj(data, ConfigInfo.class);
    }

    @Test
    public void findByDataIdAndGroupIdAndTenantIdTest() {
        List<ConfigInfo> list = configInfoRepository.findByDataIdAndGroupIdAndTenantId(configInfo.getDataId(),
            configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void findAllTest() {
        BooleanBuilder builder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        builder.and(qConfigInfo.dataId.eq(configInfo.getDataId()));
        builder.and(qConfigInfo.appName.eq(configInfo.getAppName()));
        Page<ConfigInfo> page = configInfoRepository.findAll(builder, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("gmtCreate"))));
        Assert.assertEquals(page.get().count(), 2);
    }


    @Test
    public void deleteTest() {
        Iterable<ConfigInfo> iterable = configInfoRepository.findAll();
        iterable.forEach(s -> configInfoRepository.delete(s));
    }

    @Test
    public void saveTest() {
        configInfoRepository.save(configInfo);
    }


}
