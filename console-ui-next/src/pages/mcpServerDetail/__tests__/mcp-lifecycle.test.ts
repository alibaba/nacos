import { describe, expect, it } from 'vitest';
import { getMcpVersionActions, isMcpLifecycleUnavailable } from '../mcp-lifecycle';

describe('MCP lifecycle actions', () => {
  it('maps every version state to only valid UI actions', () => {
    expect(getMcpVersionActions('draft', null, true)).toEqual([
      'editDraft',
      'submit',
      'deleteDraft',
    ]);
    expect(getMcpVersionActions('reviewing', { executionId: '1', status: 'IN_PROGRESS', pipeline: [] }, true))
      .toEqual(['publish']);
    expect(getMcpVersionActions('reviewed', { executionId: '1', status: 'APPROVED', pipeline: [] }, true)).toEqual([
      'submit',
      'publish',
      'redraft',
    ]);
    expect(getMcpVersionActions('online', null, true)).toEqual(['offline']);
    expect(getMcpVersionActions('offline', null, true)).toEqual(['online']);
  });

  it('offers force publish only to an administrator after rejection', () => {
    const rejected = { executionId: '1', status: 'REJECTED' as const, pipeline: [] };
    expect(getMcpVersionActions('draft', rejected, true)).toEqual([
      'editDraft',
      'submit',
      'deleteDraft',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewed', rejected, true)).toEqual([
      'submit',
      'publish',
      'redraft',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewing', rejected, true)).toEqual([
      'submit',
      'publish',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewed', rejected, false)).not.toContain('forcePublish');
  });

  it('recognizes only the managed-cutover conflict as lifecycle unavailable', () => {
    expect(isMcpLifecycleUnavailable({
      response: {
        status: 409,
        data: { message: 'unavailable before LIFECYCLE_MANAGED cutover' },
      },
    })).toBe(true);
    expect(isMcpLifecycleUnavailable({ response: { status: 409, data: { message: 'conflict' } } }))
      .toBe(false);
    expect(isMcpLifecycleUnavailable({ response: { status: 500 } })).toBe(false);
  });
});
