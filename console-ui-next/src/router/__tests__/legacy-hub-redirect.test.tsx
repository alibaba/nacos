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
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LegacyHubRedirect } from '../LegacyHubRedirect';

vi.mock('react-router-dom', async importOriginal => ({
  ...await importOriginal<typeof import('react-router-dom')>(),
  Navigate: ({ to, replace }: { to: { pathname: string; search: string; hash: string }; replace: boolean }) =>
    <a href={`${to.pathname}${to.search}${to.hash}`} data-replace={replace} />,
}));

function render(path: string) {
  return renderToStaticMarkup(<MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path="hub/skills" element={<LegacyHubRedirect />} />
      <Route path="hub/skills/:hubEntryId" element={<LegacyHubRedirect />} />
    </Routes>
  </MemoryRouter>);
}

describe('legacy shared Skill bookmarks', () => {
  it('redirects the directory into Skill management with its selected namespace', () => {
    expect(render('/hub/skills?namespace=team-a')).toContain('href="/skill/shared/list?namespace=team-a"');
    expect(render('/hub/skills')).toContain('data-replace="true"');
  });

  it('preserves entry identity without converting Java long IDs to numbers', () => {
    expect(render('/hub/skills/9223372036854775807#resources'))
      .toContain('href="/skill/shared/detail/9223372036854775807#resources"');
  });

  it('routes malformed legacy IDs to the directory', () => {
    expect(render('/hub/skills/not-an-id')).toContain('href="/skill/shared/list"');
  });
});
