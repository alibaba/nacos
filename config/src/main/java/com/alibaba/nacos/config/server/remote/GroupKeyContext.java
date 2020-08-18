/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

/**
 * groupy key in listen context.
 *
 * @author liuzunfei
 * @version $Id: GroupKeyContext.java, v 0.1 2020年08月17日 11:46 AM liuzunfei Exp $
 */
public class GroupKeyContext {
    
    public GroupKeyContext(String groupkey, String md5) {
        this.groupkey = groupkey;
        this.md5 = md5;
    }
    
    String groupkey;
    
    String md5;
    
    /**
     * Getter method for property <tt>groupkey</tt>.
     *
     * @return property value of groupkey
     */
    public String getGroupkey() {
        return groupkey;
    }
    
    /**
     * Setter method for property <tt>groupkey</tt>.
     *
     * @param groupkey value to be assigned to property groupkey
     */
    public void setGroupkey(String groupkey) {
        this.groupkey = groupkey;
    }
    
    /**
     * Getter method for property <tt>md5</tt>.
     *
     * @return property value of md5
     */
    public String getMd5() {
        return md5;
    }
    
    /**
     * Setter method for property <tt>md5</tt>.
     *
     * @param md5 value to be assigned to property md5
     */
    public void setMd5(String md5) {
        this.md5 = md5;
    }
}