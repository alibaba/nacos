import type { McpToolMeta } from '@/types/mcp';

const VALID_ARG_POSITIONS = new Set(['query', 'path', 'header', 'cookie', 'body']);

type ArgsPositionParseResult =
  | { ok: true; value?: Record<string, string> }
  | { ok: false; reason: 'invalidJson' | 'invalidArgsPosition' };

interface JsonTemplateFields {
  requestTemplate?: Record<string, unknown>;
  argsPosition?: Record<string, string>;
  responseTemplate?: Record<string, unknown>;
  errorResponseTemplate?: string;
}

export function parseArgsPosition(text: string): ArgsPositionParseResult {
  if (!text.trim()) {
    return { ok: true };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, reason: 'invalidJson' };
  }

  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { ok: false, reason: 'invalidArgsPosition' };
  }

  const entries = Object.entries(parsed);
  if (
    entries.some(
      ([key, value]) =>
        !key.trim() || typeof value !== 'string' || !VALID_ARG_POSITIONS.has(value)
    )
  ) {
    return { ok: false, reason: 'invalidArgsPosition' };
  }

  return entries.length > 0
    ? { ok: true, value: parsed as Record<string, string> }
    : { ok: true };
}

export function mergeJsonTemplateFields(
  templates: McpToolMeta['templates'],
  fields: JsonTemplateFields
): McpToolMeta['templates'] | undefined {
  const nextTemplates = { ...(templates || {}) };
  const jsonTemplate = { ...(nextTemplates['json-go-template'] || {}) };

  if (fields.requestTemplate) {
    jsonTemplate.requestTemplate = fields.requestTemplate;
  } else {
    delete jsonTemplate.requestTemplate;
  }
  if (fields.argsPosition) {
    jsonTemplate.argsPosition = fields.argsPosition;
  } else {
    delete jsonTemplate.argsPosition;
  }
  if (fields.responseTemplate) {
    jsonTemplate.responseTemplate = fields.responseTemplate;
  } else {
    delete jsonTemplate.responseTemplate;
  }
  if (fields.errorResponseTemplate?.trim()) {
    jsonTemplate.errorResponseTemplate = fields.errorResponseTemplate;
  } else {
    delete jsonTemplate.errorResponseTemplate;
  }

  if (Object.keys(jsonTemplate).length > 0) {
    nextTemplates['json-go-template'] = jsonTemplate;
  } else {
    delete nextTemplates['json-go-template'];
  }

  return Object.keys(nextTemplates).length > 0 ? nextTemplates : undefined;
}
