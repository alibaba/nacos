/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api', () => ({ serverApi: { getState: vi.fn() } }));

const { serverApi } = await import('@/api');
const { useServerStore } = await import('../server-store');

describe('server Hub capability', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useServerStore.setState(useServerStore.getInitialState(), true);
  });

  it('hides Hub until server state has loaded', () => {
    expect(useServerStore.getState().hubEnabled).toBe(false);
    expect(useServerStore.getState().stateLoaded).toBe(false);
  });

  it.each([
    ['true', true], [true, true], ['false', false], [false, false],
    [undefined, false],
  ])('reads the flat ai_hub_enabled value %s', async (value, enabled) => {
    vi.mocked(serverApi.getState).mockResolvedValue({ ai_hub_enabled: value } as never);
    await useServerStore.getState().fetchState();
    expect(useServerStore.getState().hubEnabled).toBe(enabled);
    expect(useServerStore.getState().stateLoaded).toBe(true);
  });

  it('updates an enabled session when the server disables Hub', async () => {
    vi.mocked(serverApi.getState)
      .mockResolvedValueOnce({ ai_hub_enabled: 'true' } as never)
      .mockResolvedValueOnce({ ai_hub_enabled: 'false' } as never);
    await useServerStore.getState().fetchState();
    expect(useServerStore.getState().hubEnabled).toBe(true);
    await useServerStore.getState().fetchState();
    expect(useServerStore.getState().hubEnabled).toBe(false);
  });
});
