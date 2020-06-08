/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
