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
package com.alibaba.nacos.naming.consistency.weak.tree;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @author satjd
 */
public class TreePeer implements Comparable<TreePeer>{
    public String ip;
    public int port;
    public String key;


    // todo hashcode , equals , compareTo
    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof TreePeer)) {
            return false;
        }

        TreePeer other = (TreePeer) obj;

        return StringUtils.equals(key, other.key);
    }

    @Override
    public int compareTo(TreePeer o) {
        return this.key.compareTo(o.key);
    }

    @Override
    public String toString() {
        return "TreePeer-" + ip + ":" + port;
    }
}
