import { describe, expect, it } from 'vitest';

import { parsePipelineInfo } from '../pipeline';

describe('parsePipelineInfo', () => {
  it('parses a valid pipeline result', () => {
    expect(parsePipelineInfo(JSON.stringify({
      executionId: 'execution-1',
      status: 'REJECTED',
      pipeline: [],
    }))).toEqual({
      executionId: 'execution-1',
      status: 'REJECTED',
      pipeline: [],
    });
  });

  it('rejects absent, malformed, or incomplete input', () => {
    expect(parsePipelineInfo(undefined)).toBeNull();
    expect(parsePipelineInfo('{')).toBeNull();
    expect(parsePipelineInfo('{"status":"REJECTED"}')).toBeNull();
  });
});
