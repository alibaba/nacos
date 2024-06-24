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

package com.alibaba.nacos.core.listener;

import com.alibaba.nacos.common.utils.ArrayUtils;
import com.alibaba.nacos.core.code.StandaloneProfileApplicationListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static com.alibaba.nacos.sys.env.Constants.STANDALONE_SPRING_PROFILE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StandaloneProfileApplicationListener} Test.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = StandaloneProfileApplicationListenerTest.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StandaloneProfileApplicationListenerTest {
    
    @Autowired
    private Environment environment;
    
    @BeforeAll
    static void init() {
        System.setProperty("nacos.standalone", "true");
    }
    
    @Test
    void testProfile() {
        assertTrue(ArrayUtils.contains(environment.getActiveProfiles(), STANDALONE_SPRING_PROFILE));
    }
    
}
