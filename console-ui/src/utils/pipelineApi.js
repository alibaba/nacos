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

import { request } from '@/globalLib';

/** Console pipeline API — list executions (pagination). */
export const PIPELINE_CONSOLE_LIST_PATH = 'v3/console/ai/pipelines/list';

/** Console pipeline API — single execution by id (query parameter pipelineId). */
export const PIPELINE_CONSOLE_DETAIL_PATH = 'v3/console/ai/pipelines/detail';

/**
 * GET detail — pipelineId matches server PipelineDetailForm.
 *
 * @param {string} pipelineId execution id (UUID)
 * @param {{ success: function, error?: function }} callbacks jQuery-style callbacks
 */
export function fetchPipelineExecutionDetail(pipelineId, callbacks) {
  const params = new URLSearchParams();
  params.append('pipelineId', pipelineId);
  request({
    url: `${PIPELINE_CONSOLE_DETAIL_PATH}?${params.toString()}`,
    success: callbacks.success,
    error: callbacks.error,
  });
}

/**
 * GET list — requires resourceType, pageNo, pageSize.
 *
 * @param {Object} query resourceType, resourceName?, namespaceId?, version?, pageNo, pageSize
 * @param {{ success: function, error?: function }} callbacks jQuery-style callbacks
 */
export function fetchPipelineExecutionList(query, callbacks) {
  const params = new URLSearchParams();
  params.append('resourceType', query.resourceType);
  if (query.resourceName) {
    params.append('resourceName', query.resourceName);
  }
  if (query.namespaceId) {
    params.append('namespaceId', query.namespaceId);
  }
  if (query.version) {
    params.append('version', query.version);
  }
  params.append('pageNo', String(query.pageNo != null ? query.pageNo : 1));
  params.append('pageSize', String(query.pageSize != null ? query.pageSize : 10));
  request({
    url: `${PIPELINE_CONSOLE_LIST_PATH}?${params.toString()}`,
    success: callbacks.success,
    error: callbacks.error,
  });
}

/**
 * Maps PipelineExecution JSON to the shape used by Prompt/Skill detail (publish pipeline info).
 *
 * @param {Object} exec execution payload from Result.data
 * @returns {Object|null} { executionId, status, pipeline }
 */
export function mapExecutionToPipelineInfo(exec) {
  if (!exec) {
    return null;
  }
  return {
    executionId: exec.executionId,
    status: exec.status,
    pipeline: exec.pipeline || [],
  };
}
