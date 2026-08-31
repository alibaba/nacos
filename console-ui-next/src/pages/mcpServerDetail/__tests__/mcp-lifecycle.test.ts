import { describe, expect, it } from 'vitest';
import { getMcpVersionActions, isMcpLifecycleUnavailable } from '../mcp-lifecycle';

describe('MCP lifecycle actions', () => {
  it('maps every version state to only valid UI actions', () => {
    expect(getMcpVersionActions('draft')).toEqual([
      'editDraft',
      'submit',
      'forcePublish',
      'deleteDraft',
    ]);
    expect(getMcpVersionActions('reviewing')).toEqual(['forcePublish']);
    expect(getMcpVersionActions('reviewed')).toEqual([
      'publish',
      'forcePublish',
      'redraft',
    ]);
    expect(getMcpVersionActions('online')).toEqual(['offline']);
    expect(getMcpVersionActions('offline')).toEqual(['online']);
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
