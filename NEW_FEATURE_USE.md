#### 命令行参数

```shell script
-Dnacos.serializer-type=kryo
# -Dnacos.serializer-type=protostuff
```

#### application.properties

```properties
#*************** Core Related Configurations ***************#

### Whether to turn on inter-member discovery, If this configuration is enabled, the cluster.conf configuration
### of the newly added member requires that a member of the known cluster be added to build the discovery channel
nacos.core.member.self-discovery=true

### Which nacos embedded distributed ID is turned on,
### If an external implementation is provided, the external implementation is automatically selected
nacos.core.idGenerator.type=default
### The step size for each fetch of the embedded distributed ID
nacos.core.idGenerator.default.acquire.step=100

### If nacos.core.idGenerator.type=snakeflower, You need to set the dataCenterID manually
#nacos.core.snowflake.data-center=
### If nacos.core.idGenerator.type=snakeflower, You need to set the WorkerID manually
#nacos.core.snowflake.worker-id=

#*************** Embed Storage Related Configurations ***************#

### Whether to open embedded distributed storage in nacos cluster mode
embeddedDistributedStorage=true

#*************** Consistency Related Configurations ***************#

# About Raft

### Sets the Raft cluster election timeout, default value is 5 second
nacos.core.protocol.raft.data.election_timeout_ms=5000
### Sets the amount of time the Raft snapshot will execute periodically, default is 30 minute
nacos.core.protocol.raft.data.snapshot_interval_secs=30
### Requested retries, default value is 1
nacos.core.protocol.raft.data.request_failoverRetries=1
### raft internal worker threads
nacos.core.protocol.raft.data.core_thread_num=8
### Number of threads required for raft business request processing
nacos.core.protocol.raft.data.cli_service_thread_num=4
### raft linear read strategy, defaults to index
nacos.core.protocol.raft.data.read_index_type=ReadOnlySafe
### rpc request timeout, default 5 seconds
nacos.core.protocol.raft.data.rpc_request_timeout_ms=5000
### Maximum size of each file RPC (snapshot copy) request between members, default is 128 K
nacos.core.protocol.raft.data.max_byte_count_per_rpc=131072
### Maximum number of logs sent from leader to follower, default is 1024
nacos.core.protocol.raft.data.max_entries_size=1024
### Maximum body size for sending logs from leader to follower, default is 512K
nacos.core.protocol.raft.data.max_body_size=524288
### Maximum log storage buffer size, default 256K
nacos.core.protocol.raft.data.max_append_buffer_size=262144
### Election timer interval will be a random maximum outside the specified time, default is 1 second
nacos.core.protocol.raft.data.max_election_delay_ms=1000
### Specify the ratio between election timeout and heartbeat interval. Heartbeat interval is equal to
### electionTimeoutMs/electionHeartbeatFactor，One tenth by default.
nacos.core.protocol.raft.data.election_heartbeat_factor=10
### The tasks submitted to the leader accumulate the maximum batch size of a batch flush log storage. The default is 32 tasks.
nacos.core.protocol.raft.data.apply_batch=32
### Call fsync when necessary when writing logs and meta information, usually should be true
nacos.core.protocol.raft.data.sync=true
### Whether to write snapshot / raft meta-information to call fsync. The default is false. When sync is true, it is preferred to respect sync.
nacos.core.protocol.raft.data.sync_meta=false
### Internal disruptor buffer size. For applications with high write throughput, you need to increase this value. The default value is 16384.
nacos.core.protocol.raft.data.disruptor_buffer_size=16384
### Whether to enable replication of pipeline request optimization, which is enabled by default
nacos.core.protocol.raft.data.replicator_pipeline=true
### Maximum number of in-flight requests with pipeline requests enabled, default is 256
nacos.core.protocol.raft.data.max_replicator_inflight_msgs=256
### Whether to enable LogEntry checksum
nacos.core.protocol.raft.data.enable_log_entry_checksum=false

# About Distro

### Maximum interval between two data transmissions
nacos.core.protocol.distro.data.task_dispatch_period_ms=2000
### Number of keys per batch of tasks
nacos.core.protocol.distro.data.batch_sync_key_count=1000
### Task retry delay time
nacos.core.protocol.distro.data.sync_retry_delay_ms=5000
### Whether to enable the authoritative server mechanism
nacos.core.protocol.distro.data.distro_enabled=true
### Data synchronization retry strategy
nacos.core.protocol.distro.data.retry_policy=simple
```