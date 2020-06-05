package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.modules.entity.HisConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.QHisConfigInfo;
import com.alibaba.nacos.config.server.modules.repository.HisConfigInfoRepository;
import com.alibaba.nacos.console.BaseTest;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest
public class HisConfigInfoRepositoryTest extends BaseTest {

    @Autowired
    private HisConfigInfoRepository hisConfigInfoRepository;

    private HisConfigInfo hisConfigInfo;

    @Before
    public void before() {
        String data = readClassPath("test-data/his_config_info.json");
        hisConfigInfo = JacksonUtils.toObj(data, HisConfigInfo.class);
    }

    @Test
    public void insertTest() {
        hisConfigInfoRepository.save(hisConfigInfo);
    }

    @Test
    public void deleteTest() {
        Iterable<HisConfigInfo> iterable = hisConfigInfoRepository.findAll();
        iterable.forEach(s -> hisConfigInfoRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        booleanBuilder.and(qHisConfigInfo.tenantId.eq(hisConfigInfo.getTenantId()));
        Iterable<HisConfigInfo> iterable = hisConfigInfoRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
