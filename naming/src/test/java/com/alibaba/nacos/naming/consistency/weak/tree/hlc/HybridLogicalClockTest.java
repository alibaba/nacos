/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree.hlc;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author lostcharlie
 */
public class HybridLogicalClockTest {

    @Test
    public void testGenerateForSending() throws Exception {
        HybridLogicalClockCoordinator coordinator = new HybridLogicalClockCoordinator();
        ReflectionTestUtils.setField(coordinator, "maxOffset", 100);
        HybridLogicalClock timestampOne = coordinator.generateForSending();
        HybridLogicalClock timestampTwo = coordinator.generateForSending();
        Thread.sleep(150);
        HybridLogicalClock timestampThree = coordinator.generateForSending();
        Assert.assertTrue(timestampOne.smallerThan(timestampTwo));
        Assert.assertTrue(coordinator.isHappenBefore(timestampOne, timestampTwo));
        Assert.assertFalse(coordinator.isConcurrent(timestampOne, timestampTwo));
        Assert.assertTrue(coordinator.isHappenBefore(timestampOne, timestampThree));
        Assert.assertTrue(coordinator.isHappenBefore(timestampTwo, timestampThree));
    }

    @Test
    public void testGenerateForReceiving() throws Exception {
        HybridLogicalClockCoordinator coordinatorOne = new HybridLogicalClockCoordinator();
        HybridLogicalClockCoordinator coordinatorTwo = new HybridLogicalClockCoordinator();
        ReflectionTestUtils.setField(coordinatorOne, "maxOffset", 100);
        ReflectionTestUtils.setField(coordinatorTwo, "maxOffset", 100);
        HybridLogicalClock timestampOne = coordinatorOne.generateForSending();
        HybridLogicalClock timestampTwo = coordinatorTwo.generateForSending();
        HybridLogicalClock timestampThree = coordinatorTwo.generateForReceiving(timestampOne);
        HybridLogicalClock timestampFour = coordinatorOne.generateForReceiving(timestampTwo);
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampOne, timestampFour));
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampTwo, timestampThree));
        Assert.assertTrue(coordinatorOne.isConcurrent(timestampOne, timestampTwo));
        Assert.assertTrue(coordinatorOne.isConcurrent(timestampThree, timestampFour));
    }

    @Test
    public void testHappenBefore() throws Exception {
        HybridLogicalClockCoordinator coordinatorOne = new HybridLogicalClockCoordinator();
        HybridLogicalClockCoordinator coordinatorTwo = new HybridLogicalClockCoordinator();
        ReflectionTestUtils.setField(coordinatorOne, "maxOffset", 100);
        ReflectionTestUtils.setField(coordinatorTwo, "maxOffset", 100);
        HybridLogicalClock timestampOne = coordinatorOne.generateForSending();
        HybridLogicalClock timestampTwo = coordinatorTwo.generateForSending();
        Thread.sleep(150);
        HybridLogicalClock timestampThree = coordinatorOne.generateForSending();
        HybridLogicalClock timestampFour = coordinatorTwo.generateForSending();
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampOne, timestampThree));
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampOne, timestampFour));
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampTwo, timestampThree));
        Assert.assertTrue(coordinatorOne.isHappenBefore(timestampTwo, timestampFour));
        Assert.assertTrue(coordinatorOne.isConcurrent(timestampOne, timestampTwo));
        Assert.assertTrue(coordinatorOne.isConcurrent(timestampThree, timestampFour));
    }
}
