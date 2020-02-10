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

package com.alibaba.nacos.core.distributed.raft;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class RaftSysConstants {

    public static final String RAFT_PORT = "raft_port";

    public static final String RAFT_ELECTION_TIMEOUT_MS = "raft_election_timeout_ms";

    public static final String RAFT_SNAPSHOT_INTERVAL_SECS = "raft_snapshot_interval_secs";

    // json、kryo

    public static final String RAFT_SERIALIZER_TYPE = "raft_serializer_type";

    public static final String REQUEST_FAILOVER_RETRIES = "raft_request_failoverRetries";

    public static final String RAFT_CORE_THREAD_NUM = "raft_core_thread_num";

    public static final String RAFT_CLI_SERVICE_THREAD_NUM = "raft_cli_service_thread_num";

}
