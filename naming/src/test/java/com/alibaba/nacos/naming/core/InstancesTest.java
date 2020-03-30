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
package com.alibaba.nacos.naming.core;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author lkxiaolou
 */
public class InstancesTest {

    private static MessageDigest MESSAGE_DIGEST;

    static {
        try {
            MESSAGE_DIGEST = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            MESSAGE_DIGEST = null;
        }
    }

    private static ThreadLocal<MessageDigest> MESSAGE_DIGEST_LOCAL = new ThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        }
    };

    @Test
    public void checkSumNotThreadSafe() throws Exception {

        final AtomicBoolean catchException = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (catchException.get()) {
                            break;
                        }
                        try {
                            checkSumThreadNotSafeVersion("test");
                        } catch (ArrayIndexOutOfBoundsException e) {
                            catchException.set(true);
                        }
                    }
                    countDownLatch.countDown();
                }
            });
            thread.start();
        }

        countDownLatch.await();

        assert catchException.get();
    }

    //@Test
    // 跑起来比较久，所以注释掉
    public void checkSumThreadSafe() throws Exception {

        CountDownLatch countDownLatch = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < Integer.MAX_VALUE; j++) {
                        checkSumThreadSafeVersion("test");
                    }
                    countDownLatch.countDown();
                }
            });
            thread.start();
        }
        countDownLatch.await();
    }

    private String checkSumThreadNotSafeVersion(String checkString) {
        return new BigInteger(1, MESSAGE_DIGEST.digest((checkString).getBytes(Charset.forName("UTF-8")))).toString(16);
    }

    private String checkSumThreadSafeVersion(String checkString) {
        return new BigInteger(1, MESSAGE_DIGEST_LOCAL.get().digest((checkString).getBytes(Charset.forName("UTF-8")))).toString(16);
    }
}
