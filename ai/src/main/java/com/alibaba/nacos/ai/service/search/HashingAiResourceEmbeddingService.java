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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Deterministic local embedding used until a deployment provides a model-backed implementation.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class HashingAiResourceEmbeddingService implements AiResourceEmbeddingService {
    
    private static final String MODEL = "nacos-local-hashing-embedding-v1";
    
    private static final int DIMENSION = 384;
    
    @Override
    public String model() {
        return MODEL;
    }
    
    @Override
    public int dimension() {
        return DIMENSION;
    }
    
    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        for (String token : tokens(text)) {
            long hash = hash(token);
            int index = (int) Math.floorMod(hash, DIMENSION);
            vector[index] += (hash & 1L) == 0L ? 1D : -1D;
        }
        normalize(vector);
        return vector;
    }
    
    private List<String> tokens(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 1) {
                result.add(token);
            }
        }
        for (int i = 0; i + 1 < normalized.length(); i++) {
            char first = normalized.charAt(i);
            char second = normalized.charAt(i + 1);
            if (!Character.isWhitespace(first) && !Character.isWhitespace(second)) {
                result.add(normalized.substring(i, i + 2));
            }
        }
        return result;
    }
    
    private long hash(String token) {
        CRC32 crc32 = new CRC32();
        crc32.update(token.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
    
    private void normalize(double[] vector) {
        double norm = 0D;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm <= 0D) {
            return;
        }
        double scale = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / scale;
        }
    }
}
