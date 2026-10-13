import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosHeaders, type AxiosResponseTransformer, type InternalAxiosRequestConfig } from 'axios';

vi.mock('../client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
import client from '../client';
import { hubSkillApi } from '../hub-skill';

const get = vi.mocked(client.get);
const post = vi.mocked(client.post);
const state = { hubEntryId: '9223372036854775807', metaVersion: '9007199254740993', sharedVersion: '1.0.0', active: true };
// The shared Axios interceptor returns the Nacos body directly.
const respond = (data: unknown) => get.mockResolvedValue(data);

beforeEach(() => vi.resetAllMocks());
describe('Hub Console transport contract', () => {
  it('preserves each tag including embedded commas through indexed form fields', async () => {
    respond({ code: 0, data: { totalCount: 0, pageItems: [] } });
    await hubSkillApi.list({ tagsAll: ['search,tools', 'coding'], pageNo: 2, pageSize: 12 });
    expect(get).toHaveBeenCalledWith('v3/console/ai/hub/skills/list', { params: {
      query: undefined, pageNo: 2, pageSize: 12, 'tagsAll[0]': 'search,tools', 'tagsAll[1]': 'coding',
    } });
  });
  it('only a never-shared source permits absent null data', async () => {
    respond({ code: 0 });
    expect((await hubSkillApi.sourceShare('ns-a', 'find-skills')).data).toBeNull();
    await expect(hubSkillApi.governanceState('901')).rejects.toThrow('Missing');
    await expect(hubSkillApi.detail('901')).rejects.toThrow('Missing');
    await expect(hubSkillApi.content('901')).rejects.toThrow('Missing');
    await expect(hubSkillApi.list({ pageNo: 1, pageSize: 12 })).rejects.toThrow('Missing');
  });
  it('capability requests preserve full long strings and suppress expected permission toasts', async () => {
    respond({ code: 0, data: state });
    expect((await hubSkillApi.governanceState(state.hubEntryId)).data).toEqual(state);
    expect(get).toHaveBeenCalledWith('v3/console/ai/hub/skills/governance-state', {
      params: { hubEntryId: state.hubEntryId }, silentError: true,
    });
  });
  it('rejects state for another entry and lossy numeric optimistic lock versions', async () => {
    respond({ code: 0, data: state });
    await expect(hubSkillApi.governanceState('901')).rejects.toThrow('Mismatched');
    respond({ code: 0, data: { ...state, metaVersion: 9007199254740992 } });
    await expect(hubSkillApi.sourceShare('ns-a', 'find-skills')).rejects.toThrow('Invalid');
  });
  it('HTTP 200 business failures retain denied or conflict codes', async () => {
    for (const code of [10001, 20004, 20005]) {
      respond({ code, message: 'denied or unavailable' });
      await expect(hubSkillApi.governanceState('901')).rejects.toMatchObject({ code });
    }
  });
  it('transport denial is propagated without fallback to an admin identity', async () => {
    const denial = { response: { status: 403 } };
    get.mockRejectedValue(denial);
    await expect(hubSkillApi.governanceState('901')).rejects.toBe(denial);
    expect(post).not.toHaveBeenCalled();
  });
  it('copy conflicts preserve the requested independent target and do not retry overwriting it', async () => {
    post.mockResolvedValue({ code: 20005, message: 'target exists' });
    const target = { hubEntryId: state.hubEntryId, targetNamespaceId: 'tenant-a', targetName: 'renamed', targetVersion: '0.0.1' };
    await expect(hubSkillApi.import(target)).rejects.toMatchObject({ code: 20005 });
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith('v3/console/ai/hub/skills/import', target, { timeout: 60000 });
  });
});

describe('fixed Hub ZIP download', () => {
  it('preserves large entry ids and binary responses with version headers', async () => {
    const blob = new Blob([new Uint8Array([80, 75, 0, 255])], { type: 'application/zip' });
    respond({ blob, hubEntryId: state.hubEntryId, sharedVersion: '1.0.0' });
    expect(await hubSkillApi.download(state.hubEntryId, '1.0.0')).toBe(blob);
    const config = get.mock.calls[0][1]!;
    expect(config.params).toEqual({ hubEntryId: state.hubEntryId });
    expect(config.responseType).toBe('blob');
    expect(config.timeout).toBe(60000);
    const transform = (config.transformResponse as AxiosResponseTransformer[])[0];
    const headers = new AxiosHeaders({
      'x-nacos-hub-entry-id': state.hubEntryId,
      'x-nacos-skill-resolved-version': '1.0.0',
    });
    expect(transform.call(config as InternalAxiosRequestConfig, blob, headers, 200))
      .toEqual({ blob, hubEntryId: state.hubEntryId, sharedVersion: '1.0.0' });
  });
  it('refuses a ZIP for a changed Hub publication instead of saving it under the old version', async () => {
    respond({ blob: new Blob(['zip'], { type: 'application/zip' }), hubEntryId: state.hubEntryId, sharedVersion: '2.0.0' });
    await expect(hubSkillApi.download(state.hubEntryId, '1.0.0')).rejects.toMatchObject({ code: 20005 });
  });
  it('never saves a JSON business denial returned with HTTP 200 as a ZIP', async () => {
    respond({ blob: new Blob([JSON.stringify({ code: 10001, message: 'denied' })], { type: 'application/json' }) });
    await expect(hubSkillApi.download('901', '1.0.0')).rejects.toMatchObject({ code: 10001 });
  });
});
