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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Page.
 *
 * @author boyan
 * @date 2010-5-6
 */
public class Page<E> implements Serializable {
    
    static final long serialVersionUID = -1L;
    
    /**
     * totalCount.
     */
    private int totalCount;
    
    /**
     * pageNumber.
     */
    private int pageNumber;
    
    /**
     * pagesAvailable.
     */
    private int pagesAvailable;
    
    /**
     * pageItems.
     */
    private List<E> pageItems = new ArrayList<E>();
    
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    public void setPagesAvailable(int pagesAvailable) {
        this.pagesAvailable = pagesAvailable;
    }
    
    public void setPageItems(List<E> pageItems) {
        this.pageItems = pageItems;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getPageNumber() {
        return pageNumber;
    }
    
    public int getPagesAvailable() {
        return pagesAvailable;
    }
    
    public List<E> getPageItems() {
        return pageItems;
    }
}
