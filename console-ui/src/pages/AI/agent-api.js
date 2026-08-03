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

const BASE = 'v3/console/ai/agents';

function query(params) {
  const result = new URLSearchParams();
  Object.keys(params || {}).forEach(key => {
    const value = params[key];
    if (value !== undefined && value !== null && value !== '') {
      result.append(key, String(value));
    }
  });
  return result.toString();
}

function invoke(options) {
  return new Promise((resolve, reject) => {
    request({
      ...options,
      success: result => {
        if (result && (result.code === 0 || result.code === 200)) {
          resolve(result.data);
        } else {
          reject(new Error((result && result.message) || 'Agent request failed'));
        }
      },
      error: error => reject(error instanceof Error ? error : new Error('Agent request failed')),
    });
  });
}

function read(path, params) {
  const suffix = query(params);
  return invoke({ url: `${BASE}${path}${suffix ? `?${suffix}` : ''}`, method: 'GET' });
}

function write(path, method, data) {
  return invoke({
    url: `${BASE}${path}`,
    method,
    data,
    contentType: 'application/x-www-form-urlencoded',
  });
}

function remove(path, params) {
  const suffix = query(params);
  return invoke({ url: `${BASE}${path}?${suffix}`, method: 'DELETE' });
}

export const agentApi = {
  list: params => read('/list', params),
  get: params => read('', params),
  update: data => write('', 'PUT', data),
  delete: params => remove('', params),
  versions: params => read('/versions', params),
  version: params => read('/version', params),
  runtime: params => read('/runtime-endpoints', params),
  createDraft: data => write('/draft', 'POST', data),
  updateDraft: data => write('/draft', 'PUT', data),
  deleteDraft: params => remove('/draft', params),
  submit: data => write('/submit', 'POST', data),
  publish: data => write('/publish', 'POST', data),
  forcePublish: data => write('/force-publish', 'POST', data),
  redraft: data => write('/redraft', 'POST', data),
  online: data => write('/online', 'POST', data),
  offline: data => write('/offline', 'POST', data),
  updateLabels: data => write('/labels', 'PUT', data),
};

export { BASE };
