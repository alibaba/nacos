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

package com.alibaba.nacos.core.cluster;

import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface MemberManager {

    /**
     * node manager init
     */
    void init();

    /**
     * this address which index in NodeManager
     * start from 1
     *
     * @param address ip:port info
     * @return this address index in node collection
     */
    int indexOf(String address);

    /**
     * update self-node info
     *
     * @param member
     */
    void update(Member member);

    /**
     * Determine if the node exists according to ip: port or ip,
     * If address contains only ip information, it returns true
     * as long as the ip exists in the node list
     *
     * @param address ip:port
     * @return exist result
     */
    boolean hasMember(String address);

    /**
     * get self node
     *
     * @return {@link Member}
     */
    Member self();

    /**
     * this node ip is the first in node collection
     *
     * @return is first ip in node collection
     */
    boolean isFirstIp();

    /**
     * list all nodes which status is health
     *
     * @return {@link Collection< Member >}
     */
    Collection<Member> allMembers();

    /**
     * New node join
     *
     * @param member
     */
    void memberJoin(Collection<Member> member);

    /**
     * One node Leave
     *
     * @param member
     */
    void memberLeave(Collection<Member> member);

    /**
     * subscribe node change event
     *
     * @param listener {@link MemberChangeListener}
     */
    void subscribe(MemberChangeListener listener);

    /**
     * unsubscribe node change event
     *
     * @param listener {@link MemberChangeListener}
     */
    void unSubscribe(MemberChangeListener listener);

    /**
     * get web-context path
     *
     * @return path
     */
    String getContextPath();

    /**
     * clean operation
     */
    void clean();

    /**
     * node manager shutdown
     */
    void shutdown();

}
