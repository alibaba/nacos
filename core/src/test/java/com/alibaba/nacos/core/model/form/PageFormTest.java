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

package com.alibaba.nacos.core.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PageForm} unit test.
 */
class PageFormTest {
    
    @Test
    void defaultValues() {
        PageForm form = new PageForm();
        assertEquals(1, form.getPageNo());
        assertEquals(100, form.getPageSize());
    }
    
    @Test
    void validateSuccessWithDefaults() throws NacosApiException {
        PageForm form = new PageForm();
        form.validate();
    }
    
    @Test
    void validateSuccessWithCustomValues() throws NacosApiException {
        PageForm form = new PageForm();
        form.setPageNo(2);
        form.setPageSize(50);
        form.validate();
        assertEquals(2, form.getPageNo());
        assertEquals(50, form.getPageSize());
    }
    
    @Test
    void validateThrowsWhenPageNoLessThanOne() {
        PageForm form = new PageForm();
        form.setPageNo(0);
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(400, ex.getErrCode());
        assertEquals("Required parameter 'pageNo' should be positive integer, current is 0",
            ex.getErrMsg());
    }
    
    @Test
    void validateThrowsWhenPageSizeLessThanOne() {
        PageForm form = new PageForm();
        form.setPageSize(0);
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(400, ex.getErrCode());
        assertEquals("Required parameter 'pageSize' should be positive integer, current is 0",
            ex.getErrMsg());
    }
    
    @Test
    void gettersAndSetters() {
        PageForm form = new PageForm();
        form.setPageNo(5);
        form.setPageSize(20);
        assertEquals(5, form.getPageNo());
        assertEquals(20, form.getPageSize());
    }
}
