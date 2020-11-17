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

package com.alibaba.nacos.naming.consistency.ephemeral;

import com.alibaba.nacos.naming.consistency.ConsistencyService;

/**
 * A type of consistency for ephemeral data.
 *
 * <p>This kind of consistency is not required to store data on disk or database, because the ephemeral data always
 * keeps a session with server and as long as the session still lives the ephemeral data won't be lost.
 *
 * <p>What is required is that writing should always be successful even if network partition happens. And when the
 * network recovers, data of each partition is merged into one set, so the cluster resumes to a consistent status.
 *
 * @author nkorange
 * @since 1.0.0
 */
public interface EphemeralConsistencyService extends ConsistencyService {

}
