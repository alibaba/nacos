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

package com.alibaba.nacos.core.distributed.distro.utils;

import com.alibaba.nacos.core.utils.Loggers;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DistroUtils {

    private static MessageDigest MESSAGE_DIGEST;

    static {
        try {
            MESSAGE_DIGEST = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Loggers.DISTRO.error("error while calculating checksum(md5) for instances", e);
            MESSAGE_DIGEST = null;
        }
    }

    /**
     * Provide a number between 0(inclusive) and {@code upperLimit}(exclusive) for the given {@code string},
     * the number will be nearly uniform distribution.
     * <p>
     * <p>
     * <p>
     * e.g. Assume there's an array which contains some IP of the servers provide the same service,
     * the caller name can be used to choose the server to achieve load balance.
     * <blockquote><pre>
     *     String[] serverIps = new String[10];
     *     int index = shakeUp("callerName", serverIps.length);
     *     String targetServerIp = serverIps[index];
     * </pre></blockquote>
     *
     * @param string     a string. the number 0 will be returned if it's null
     * @param upperLimit the upper limit of the returned number, must be a positive integer, which means > 0
     * @return a number between 0(inclusive) and upperLimit(exclusive)
     * @throws IllegalArgumentException if the upper limit equals or less than 0
     * @author jifengnan
     * @since 1.0.0
     */
    public static int shakeUp(String string, int upperLimit) {
        if (upperLimit < 1) {
            throw new IllegalArgumentException("upper limit must be greater than 0");
        }
        if (string == null) {
            return 0;
        }
        return (string.hashCode() & Integer.MAX_VALUE) % upperLimit;
    }

    public static String checkSum(byte[] bytes) {

        String checksum;

        if (MESSAGE_DIGEST != null) {
            checksum =
                    new BigInteger(1, MESSAGE_DIGEST.digest(bytes)).toString(16);
        } else {
            checksum = RandomStringUtils.randomAscii(32);
        }
        return checksum;

    }

}
