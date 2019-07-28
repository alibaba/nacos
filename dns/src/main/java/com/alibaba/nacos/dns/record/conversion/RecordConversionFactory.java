/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.record.conversion;

import com.alibaba.nacos.dns.record.RecordType;

/**
 * @author paderlol
 * @date 2019年07月28日, 16:29:20
 */
public interface RecordConversionFactory {

    /**
     * Create record conversion.
     *
     * @param recordType the record type
     * @return the record conversion
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:29:20
     */
    RecordConversion create(RecordType recordType);
}
