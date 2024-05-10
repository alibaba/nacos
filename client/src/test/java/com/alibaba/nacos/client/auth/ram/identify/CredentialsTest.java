/*
 *
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
 *
 */

package com.alibaba.nacos.client.auth.ram.identify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialsTest {

    @Test
    void testGetter() {
        // given
        String ak = "ak";
        String sk = "sk";
        String tenantId = "100";
        Credentials credentials = new Credentials(ak, sk, tenantId);
        // when  then
        assertEquals(ak, credentials.getAccessKey());
        assertEquals(sk, credentials.getSecretKey());
        assertEquals(tenantId, credentials.getTenantId());
    }

    @Test
    void testSetter() {
        //given
        String ak = "ak";
        String sk = "sk";
        String tenantId = "100";
        Credentials credentials = new Credentials();
        //when
        credentials.setAccessKey(ak);
        credentials.setSecretKey(sk);
        credentials.setTenantId(tenantId);
        //then
        assertEquals(ak, credentials.getAccessKey());
        assertEquals(sk, credentials.getSecretKey());
        assertEquals(tenantId, credentials.getTenantId());
    }

    @Test
    void testValid() {
        //given
        String ak = "ak";
        String sk = "sk";
        String tenantId = "100";
        Credentials credentials = new Credentials(ak, sk, tenantId);
        //when
        boolean actual = credentials.valid();
        //then
        assertTrue(actual);
    }

    @Test
    void testIdentical() {
        //given
        String ak = "ak";
        String sk = "sk";
        String tenantId = "100";
        Credentials credentials1 = new Credentials(ak, sk, "101");
        Credentials credentials2 = new Credentials(ak, sk, "100");
        //then
        boolean actual = credentials1.identical(credentials2);
        //then
        assertTrue(actual);
    }
}
