/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.nio.charset.Charset;
import java.util.Collection;

/**
 * To expose interface from {@link PathEncoder}.
 *
 * @author daydreamer-ia
 */
public class PathEncoderManager {

    /**
     * singleton.
     */
    private static final PathEncoderManager INSTANCE = new PathEncoderManager();

    /**
     * encoder.
     */
    private PathEncoder targetEncoder = null;

    private PathEncoderManager() {
        // load path encoder
        Collection<PathEncoder> load = NacosServiceLoader.load(PathEncoder.class);
        if (!load.isEmpty()) {
            String currentOs = System.getProperty("os.name").toLowerCase();
            for (PathEncoder pathEncoder : load) {
                // match first
                if (currentOs.contains(pathEncoder.name())) {
                    targetEncoder = pathEncoder;
                    break;
                }
            }
        }
    }

    /**
     * encode path if necessary.
     *
     * @param path    origin path
     * @param charset charset of origin path
     * @return encoded path
     */
    public String encode(String path, String charset) {
        if (path == null || charset == null) {
            return path;
        }
        if (targetEncoder != null && targetEncoder.needEncode(path)) {
            return targetEncoder.encode(path, charset);
        }
        return path;
    }

    /**
     * encode path if necessary.
     *
     * @param path origin path
     * @return encoded path
     */
    public String encode(String path) {
        return encode(path, Charset.defaultCharset().name());
    }

    /**
     * decode path.
     *
     * @param path    encoded path
     * @param charset charset of encoded path
     * @return origin path
     */
    public String decode(String path, String charset) {
        if (path == null || charset == null) {
            return path;
        }
        if (targetEncoder != null) {
            return targetEncoder.decode(path, charset);
        }
        return path;
    }

    /**
     * decode path.
     *
     * @param path encoded path
     * @return origin path
     */
    public String decode(String path) {
        return decode(path, Charset.defaultCharset().name());
    }

    /**
     * get singleton.
     *
     * @return singleton.
     */
    public static PathEncoderManager getInstance() {
        return INSTANCE;
    }

}
