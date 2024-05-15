/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NamingHttpUtilTest {
    
    @Test
    void testBuilderHeader() {
        Header header = NamingHttpUtil.builderHeader();
        assertNotNull(header);
        assertEquals(header.getValue(HttpHeaderConsts.CLIENT_VERSION_HEADER), VersionUtils.version);
        assertEquals(header.getValue(HttpHeaderConsts.USER_AGENT_HEADER), VersionUtils.getFullClientVersion());
        assertEquals("gzip,deflate,sdch", header.getValue(HttpHeaderConsts.ACCEPT_ENCODING));
        assertEquals("Keep-Alive", header.getValue(HttpHeaderConsts.CONNECTION));
        assertNotNull(header.getValue(HttpHeaderConsts.REQUEST_ID));
        assertEquals("Naming", header.getValue(HttpHeaderConsts.REQUEST_MODULE));
    }
}