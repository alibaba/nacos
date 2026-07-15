import { describe, expect, it } from 'vitest';
import { mergeJsonTemplateFields, parseArgsPosition } from '../tool-template-utils';

describe('parseArgsPosition', () => {
  it.each(['query', 'path', 'header', 'cookie', 'body'])(
    'accepts the %s argument position',
    (position) => {
      expect(parseArgsPosition(JSON.stringify({ orderId: position }))).toEqual({
        ok: true,
        value: { orderId: position },
      });
    }
  );

  it('accepts mixed argument positions', () => {
    const value = {
      search: 'query',
      orderId: 'path',
      token: 'header',
      session: 'cookie',
      request: 'body',
    };

    expect(parseArgsPosition(JSON.stringify(value))).toEqual({ ok: true, value });
  });

  it('reports invalid JSON separately from invalid structures', () => {
    expect(parseArgsPosition('{')).toEqual({ ok: false, reason: 'invalidJson' });
  });

  it.each([
    ['array', '["path"]'],
    ['null', 'null'],
    ['string', '"query"'],
    ['number', '1'],
    ['boolean', 'true'],
    ['empty key', '{"": "path"}'],
    ['whitespace key', '{" ": "path"}'],
    ['non-string value', '{"orderId": 1}'],
    ['unsupported position', '{"orderId": "param"}'],
  ])('rejects %s', (_name, text) => {
    expect(parseArgsPosition(text)).toEqual({ ok: false, reason: 'invalidArgsPosition' });
  });

  it.each(['', '   ', '{}'])('treats %j as unconfigured', (text) => {
    expect(parseArgsPosition(text)).toEqual({ ok: true });
  });
});

describe('mergeJsonTemplateFields', () => {
  it('updates all supported fields and preserves extension data', () => {
    const errorResponseTemplate =
      'status={{ gjson "_headers.\\\\:status" }}\nraw={{ .data.value }}';
    const result = mergeJsonTemplateFields(
      {
        'json-go-template': { extension: { future: true } },
        'other-template': { keep: true },
      },
      {
        requestTemplate: { method: 'GET' },
        argsPosition: { orderId: 'path' },
        responseTemplate: { body: '{{ .data }}' },
        errorResponseTemplate,
      }
    );

    expect(result).toEqual({
      'json-go-template': {
        extension: { future: true },
        requestTemplate: { method: 'GET' },
        argsPosition: { orderId: 'path' },
        responseTemplate: { body: '{{ .data }}' },
        errorResponseTemplate,
      },
      'other-template': { keep: true },
    });
  });

  it('removes only cleared supported fields', () => {
    const result = mergeJsonTemplateFields(
      {
        'json-go-template': {
          requestTemplate: { method: 'GET' },
          argsPosition: { orderId: 'path' },
          responseTemplate: { body: '{{ .data }}' },
          errorResponseTemplate: 'error',
          extension: true,
        },
        'other-template': { keep: true },
      },
      {}
    );

    expect(result).toEqual({
      'json-go-template': { extension: true },
      'other-template': { keep: true },
    });
  });

  it('clears argsPosition without changing sibling fields', () => {
    const result = mergeJsonTemplateFields(
      {
        'json-go-template': {
          requestTemplate: { method: 'GET' },
          argsPosition: { orderId: 'path' },
          responseTemplate: { body: '{{ .data }}' },
          errorResponseTemplate: 'error',
        },
      },
      {
        requestTemplate: { method: 'GET' },
        responseTemplate: { body: '{{ .data }}' },
        errorResponseTemplate: 'error',
      }
    );

    expect(result?.['json-go-template']).toEqual({
      requestTemplate: { method: 'GET' },
      responseTemplate: { body: '{{ .data }}' },
      errorResponseTemplate: 'error',
    });
  });

  it('clears errorResponseTemplate without changing sibling fields', () => {
    const result = mergeJsonTemplateFields(
      {
        'json-go-template': {
          requestTemplate: { method: 'GET' },
          argsPosition: { orderId: 'path' },
          responseTemplate: { body: '{{ .data }}' },
          errorResponseTemplate: 'error',
        },
      },
      {
        requestTemplate: { method: 'GET' },
        argsPosition: { orderId: 'path' },
        responseTemplate: { body: '{{ .data }}' },
      }
    );

    expect(result?.['json-go-template']).toEqual({
      requestTemplate: { method: 'GET' },
      argsPosition: { orderId: 'path' },
      responseTemplate: { body: '{{ .data }}' },
    });
  });

  it('preserves the original error response template text', () => {
    const original = '  status={{ gjson "_headers.\\\\:status" }}\n  raw={{ .data.value }}  ';

    expect(mergeJsonTemplateFields(undefined, { errorResponseTemplate: original })).toEqual({
      'json-go-template': { errorResponseTemplate: original },
    });
  });

  it('removes empty template containers', () => {
    expect(mergeJsonTemplateFields({ 'json-go-template': { argsPosition: {} } }, {})).toBeUndefined();
  });
});
