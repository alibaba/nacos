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

import { describe, expect, it, vi } from 'vitest';
import { matchRoutes } from 'react-router-dom';
import { routes } from '../routes';

vi.mock('../guards', () => ({
  AuthGuard: () => null,
  AdminGuard: () => null,
  GuestGuard: () => null,
  AiGuard: () => null,
  HubGuard: () => null,
  getDefaultRoute: () => '/skill',
}));
vi.mock('@/stores/server-store', () => ({ useServerStore: () => ({}) }));

const matchedPath = (pathname: string) => matchRoutes(routes, pathname)?.at(-1)?.route.path;

describe('shared Skill routes', () => {
  it('keeps ordinary Skills named shared accessible through their original detail URL', () => {
    expect(matchedPath('/skill/shared')).toBe('skill/:name');
    expect(matchedPath('/skill/list')).toBe('skill/:name');
  });

  it('mounts resource-specific shared lists and entry details inside Skill management', () => {
    expect(matchedPath('/skill/shared/list')).toBe('skill/shared/list');
    expect(matchedPath('/skill/shared/detail/901')).toBe('skill/shared/detail/:hubEntryId');
  });

  it('does not enable shared routes for the four deferred resource types', () => {
    for (const path of ['mcpServerManagement', 'agentManagement', 'promptManagement', 'agentspec']) {
      expect(matchedPath(`/${path}/shared/list`)).not.toBe(`${path}/shared/list`);
      expect(matchedPath(`/${path}/shared/detail/901`)).not.toBe(`${path}/shared/detail/:hubEntryId`);
    }
  });

  it('retains old directory and detail paths as redirect routes', () => {
    expect(matchedPath('/hub/skills')).toBe('hub/skills');
    expect(matchedPath('/hub/skills/901')).toBe('hub/skills/:hubEntryId');
  });
});
