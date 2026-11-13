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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { skillApi } from '@/api/skill';
import { useSkillStore } from '../skill-store';

vi.mock('@/api/skill', () => ({ skillApi: { getDetail: vi.fn() } }));

type DetailResponse = Awaited<ReturnType<typeof skillApi.getDetail>>;
const response = { data: { labels: { latest: '1.0.0' }, versions: [] } } as unknown as DetailResponse;

function deferred() {
  let resolve!: (value: DetailResponse) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<DetailResponse>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.resetAllMocks();
  useSkillStore.getState().clearDetail();
  useSkillStore.getState().clearError();
});

describe('Skill detail request identity', () => {
  it.each([['public', 'copy'], ['team-a', 'copy'], ['public', 'source']])(
    'clears source metadata while fetching %s/%s', async (namespaceId, name) => {
      vi.mocked(skillApi.getDetail).mockResolvedValueOnce(response);
      await useSkillStore.getState().fetchDetail('team-a', 'source');
      const pending = deferred();
      vi.mocked(skillApi.getDetail).mockReturnValueOnce(pending.promise);
      const fetching = useSkillStore.getState().fetchDetail(namespaceId, name);
      expect(useSkillStore.getState().currentDetail).toBeNull();
      expect(useSkillStore.getState().detailLoading).toBe(true);
      pending.resolve(response);
      await fetching;
      expect(useSkillStore.getState().currentDetail).toMatchObject({ namespaceId, name });
    },
  );

  it.each(['success', 'error'])('ignores a late source %s after the target has loaded', async outcome => {
    const source = deferred();
    vi.mocked(skillApi.getDetail).mockReturnValueOnce(source.promise).mockResolvedValueOnce(response);
    const oldFetch = useSkillStore.getState().fetchDetail('team-a', 'source');
    await useSkillStore.getState().fetchDetail('public', 'copy');
    if (outcome === 'success') source.resolve(response);
    else source.reject(new Error('Source failed'));
    await oldFetch;
    expect(useSkillStore.getState().currentDetail).toMatchObject({ namespaceId: 'public', name: 'copy' });
    expect(useSkillStore.getState().error).toBeNull();
    expect(useSkillStore.getState().detailLoading).toBe(false);
  });

  it('does not repopulate the detail after leaving the page', async () => {
    const pending = deferred();
    vi.mocked(skillApi.getDetail).mockReturnValueOnce(pending.promise);
    const fetching = useSkillStore.getState().fetchDetail('public', 'source');
    useSkillStore.getState().clearDetail();
    pending.resolve(response);
    await fetching;
    expect(useSkillStore.getState().currentDetail).toBeNull();
    expect(useSkillStore.getState().detailLoading).toBe(false);
  });
});
