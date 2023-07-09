/*
 *  Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.common.paramcheck;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * The type Param check utils test.
 */
public class ParamCheckUtilsTest extends TestCase {
    
    /**
     * Test check param info format.
     */
    public void testCheckParamInfoFormat() {
        ParamInfo paramInfo = new ParamInfo();
        ParamCheckUtils.checkParamInfoFormat(paramInfo);
    }
    
    /**
     * Test check namespace show name format.
     */
    public void testCheckNamespaceShowNameFormat() {
        StringBuilder builder = new StringBuilder();
        ParamCheckUtils.checkNamespaceShowNameFormat(builder.toString());
        for (int i = 0; i < 256; i++) {
            builder.append("a");
        }
        ParamCheckUtils.checkNamespaceShowNameFormat(builder.toString());
        builder.append("a");
        try {
            ParamCheckUtils.checkNamespaceShowNameFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check namespace id format.
     */
    public void testCheckNamespaceIdFormat() {
        StringBuilder namespaceid = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_NAMESPACE_ID_LENGTH; i++) {
            namespaceid.append("@");
        }
        ParamCheckUtils.checkNamespaceIdFormat(namespaceid.toString());
        namespaceid.append("@");
        try {
            ParamCheckUtils.checkNamespaceIdFormat(namespaceid.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String namespaceId = "蟘";
        try {
            ParamCheckUtils.checkNamespaceIdFormat(namespaceId);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check data id format.
     */
    public void testCheckDataIdFormat() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_DATA_ID_LENGTH; i++) {
            builder.append("a");
        }
        ParamCheckUtils.checkDataIdFormat(builder.toString());
        try {
            builder.append("a");
            ParamCheckUtils.checkDataIdFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String dataid = "@##@";
        ParamCheckUtils.checkDataIdFormat(dataid);
        dataid = "你好";
        try {
            ParamCheckUtils.checkDataIdFormat(dataid);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        dataid = "@@aaaaa";
        try {
            ParamCheckUtils.checkDataIdFormat(dataid);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check service name format.
     */
    public void testCheckServiceNameFormat() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_SERVICE_NAME_LENGTH; i++) {
            builder.append("a");
        }
        ParamCheckUtils.checkServiceNameFormat(builder.toString());
        try {
            builder.append("a");
            ParamCheckUtils.checkServiceNameFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String servicename = "@##@";
        ParamCheckUtils.checkServiceNameFormat(servicename);
        servicename = "你好";
        try {
            ParamCheckUtils.checkServiceNameFormat(servicename);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        servicename = "@@aaaaa";
        try {
            ParamCheckUtils.checkServiceNameFormat(servicename);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check group format.
     */
    public void testCheckGroupFormat() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_GROUP_LENGTH; i++) {
            builder.append("a");
        }
        ParamCheckUtils.checkGroupFormat(builder.toString());
        builder.append("a");
        try {
            ParamCheckUtils.checkGroupFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String group = "@@aa";
        try {
            ParamCheckUtils.checkGroupFormat(group);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        group = "你好";
        try {
            ParamCheckUtils.checkGroupFormat(group);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check cluster format.
     */
    public void testCheckClusterFormat() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_CLUSTER_LENGTH; i++) {
            builder.append("@");
        }
        ParamCheckUtils.checkClusterFormat(builder.toString());
        builder.append("@");
        try {
            ParamCheckUtils.checkClusterFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String cluster = "你好a";
        try {
            ParamCheckUtils.checkClusterFormat(cluster);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        cluster = ",a";
        try {
            ParamCheckUtils.checkClusterFormat(cluster);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check ip format.
     */
    public void testCheckIpFormat() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ParamCheckRules.MAX_IP_LENGTH; i++) {
            builder.append("0");
        }
        ParamCheckUtils.checkIpFormat(builder.toString());
        builder.append("1");
        try {
            ParamCheckUtils.checkIpFormat(builder.toString());
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        String ip = "你好";
        try {
            ParamCheckUtils.checkIpFormat(ip);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
    
    /**
     * Test check port format.
     */
    public void testCheckPortFormat() {
        String port = "-10";
        try {
            ParamCheckUtils.checkPortFormat(port);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        port = "65536";
        try {
            ParamCheckUtils.checkPortFormat(port);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        port = "aaa";
        try {
            ParamCheckUtils.checkPortFormat(port);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        port = "0";
        ParamCheckUtils.checkPortFormat(port);
        port = "65535";
        ParamCheckUtils.checkPortFormat(port);
    }
    
    /**
     * Test check metadata format.
     */
    public void testCheckMetadataFormat() {
        Map<String, String> metadata = new HashMap<>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 511; i++) {
            builder.append("a");
        }
        metadata.put("a", builder.toString());
        metadata.put("b", builder.toString());
        ParamCheckUtils.checkMetadataFormat(metadata);
        metadata.put("c", "");
        try {
            ParamCheckUtils.checkMetadataFormat(metadata);
            fail("expected a illegalArgumentException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}