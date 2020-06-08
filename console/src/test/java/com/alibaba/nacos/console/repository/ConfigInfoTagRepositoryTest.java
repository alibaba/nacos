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
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTag;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoTag;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
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

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoTagRepositoryTest extends BaseTest {

    @Autowired
    private ConfigInfoTagRepository configInfoTagRepository;


    private ConfigInfoTag configInfoTag;

    @Before
    public void before() {
        String data = readClassPath("test-data/config_info_tag.json");
        configInfoTag = JacksonUtils.toObj(data, ConfigInfoTag.class);
    }

    @Test
    public void insertTest() {
        configInfoTagRepository.save(configInfoTag);
    }

    @Test
    public void deleteTest() {
        Iterable<ConfigInfoTag> infoTags = configInfoTagRepository.findAll();
        infoTags.forEach(s -> configInfoTagRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        booleanBuilder.and(qConfigInfoTag.appName.eq(configInfoTag.getAppName()));
        Iterable<ConfigInfoTag> iterable = configInfoTagRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
