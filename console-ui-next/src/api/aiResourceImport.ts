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

import client from './client';
import type { ApiResult } from './types';
import type {
  AiResourceImportExecuteResponse,
  AiResourceImportSearchResponse,
  AiResourceImportSourceInfo,
  AiResourceImportValidateResponse,
} from '@/types/aiResourceImport';

const IMPORT_PATH = 'v3/console/ai/import';

export const aiResourceImportApi = {
  listSources: (params: { resourceType: string }): ApiResult<AiResourceImportSourceInfo[]> =>
    client.get(`${IMPORT_PATH}/sources`, { params }) as ApiResult<AiResourceImportSourceInfo[]>,

  search: (data: {
    namespaceId?: string;
    resourceType: string;
    sourceId: string;
    query?: string;
    cursor?: string;
    limit?: number;
  }): ApiResult<AiResourceImportSearchResponse> =>
    client.post(`${IMPORT_PATH}/search`, data) as ApiResult<AiResourceImportSearchResponse>,

  validate: (data: {
    namespaceId?: string;
    resourceType: string;
    sourceId: string;
    selectedItems: string;
    overwriteExisting?: boolean;
  }): ApiResult<AiResourceImportValidateResponse> =>
    client.post(`${IMPORT_PATH}/validate`, data) as ApiResult<AiResourceImportValidateResponse>,

  execute: (data: {
    namespaceId?: string;
    resourceType: string;
    sourceId: string;
    selectedItems: string;
    overwriteExisting?: boolean;
    skipInvalid?: boolean;
    validationToken?: string;
  }): ApiResult<AiResourceImportExecuteResponse> =>
    client.post(`${IMPORT_PATH}/execute`, data) as ApiResult<AiResourceImportExecuteResponse>,
};
