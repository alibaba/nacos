/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verify that only the read endpoints opt into the existing anonymous auth policy. */
class HubSkillAnonymousReadContractTest {
    
    @Test
    void everyHubReadOptsIntoAnonymousIdentityAndKeepsNormalAuthentication() {
        int count = 0;
        for (Method method : HubSkillReadAdminController.class.getDeclaredMethods()) {
            Secured secured = method.getAnnotation(Secured.class);
            if (secured != null) {
                assertTrue(Arrays.asList(secured.tags()).contains(Constants.Tag.ALLOW_ANONYMOUS));
                assertTrue(Arrays.asList(secured.tags()).contains(Constants.Tag.ONLY_IDENTITY));
                count++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(4, count);
    }
    
    @Test
    void mutationAndGovernanceEndpointsNeverOptIntoAnonymousAccess() {
        for (Class<?> controller : new Class<?>[] {HubSkillAdminController.class,
            HubSkillTransferAdminController.class, HubSkillStateAdminController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                Secured secured = method.getAnnotation(Secured.class);
                if (secured != null) {
                    assertFalse(
                        Arrays.asList(secured.tags()).contains(Constants.Tag.ALLOW_ANONYMOUS));
                }
            }
        }
    }
}
