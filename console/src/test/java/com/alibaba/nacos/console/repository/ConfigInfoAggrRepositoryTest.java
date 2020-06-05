package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggr;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoAggr;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoAggrRepository;
import com.alibaba.nacos.console.BaseTest;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;


@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoAggrRepositoryTest extends BaseTest {

    @Autowired
    private ConfigInfoAggrRepository configInfoAggrRepository;

    private ConfigInfoAggr configInfoAggr;

    @Before
    public void before() {
        String data = readClassPath("test-data/config_info_aggr.json");
        configInfoAggr = JacksonUtils.toObj(data, ConfigInfoAggr.class);
    }

    @Test
    public void insertTest() {
        configInfoAggrRepository.save(configInfoAggr);
    }

    @Test
    public void delete() {
        Iterable<ConfigInfoAggr> infoAggrs = configInfoAggrRepository.findAll();
        infoAggrs.forEach(s -> configInfoAggrRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        booleanBuilder.and(qConfigInfoAggr.appName.eq(configInfoAggr.getAppName()));
        booleanBuilder.and(qConfigInfoAggr.dataId.eq(configInfoAggr.getDataId()));
        Iterable<ConfigInfoAggr> infoAggrs = configInfoAggrRepository.findAll(booleanBuilder);
        Assert.assertTrue(((List<ConfigInfoAggr>) infoAggrs).size() > 0);
    }

}
