/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.auth;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * {@link AuthConfig} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 13:36
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthConfigTest {
    
    @Mock
    private AuthFilter authFilter;
    
    @Test
    public void testAuthFilterRegistration() {
        AuthConfig config = new AuthConfig();
        FilterRegistrationBean filter = config.authFilterRegistration(authFilter);
        Assert.assertTrue(filter.getFilter() instanceof AuthFilter);
        
        Assert.assertTrue(filter.getUrlPatterns().contains("/*"));
        
        Assert.assertEquals(6, filter.getOrder());
    }
}
