/* eslint-disable @typescript-eslint/no-explicit-any */

interface ToolArg {
  name: string;
  description: string;
  type?: string;
  required: boolean;
  position: string; // path | query | header | cookie | body
  enum?: string[];
  items?: any;
  properties?: Record<string, any>;
  schema?: Record<string, any>;
}

interface RawTool {
  name: string;
  description: string;
  args: ToolArg[];
  requestTemplate: any;
  responseTemplate: any;
}

interface McpConfig {
  server: {
    name: string;
    securitySchemes: any[];
  };
  tools: RawTool[];
}

const JSON_SCHEMA_COPY_KEYS = [
  'type',
  'description',
  'default',
  'enum',
  'format',
  'minimum',
  'maximum',
  'exclusiveMinimum',
  'exclusiveMaximum',
  'minLength',
  'maxLength',
  'pattern',
  'minItems',
  'maxItems',
  'uniqueItems',
  'nullable',
  'additionalProperties',
  'oneOf',
  'anyOf',
] as const;

function cloneValue<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function mergeSchema(target: Record<string, any>, source: Record<string, any>) {
  for (const [key, value] of Object.entries(source)) {
    if (key === 'properties' && value && typeof value === 'object') {
      target.properties = { ...(target.properties || {}), ...value };
    } else if (key === 'required' && Array.isArray(value)) {
      target.required = Array.from(new Set([...(target.required || []), ...value]));
    } else if (target[key] === undefined) {
      target[key] = value;
    }
  }
}

function normalizeSchema(schema: any): Record<string, any> {
  if (!schema || typeof schema !== 'object') {
    return { type: 'string' };
  }

  const result: Record<string, any> = {};
  if (Array.isArray(schema.allOf)) {
    for (const item of schema.allOf) {
      mergeSchema(result, normalizeSchema(item));
    }
  }

  for (const key of JSON_SCHEMA_COPY_KEYS) {
    if (schema[key] !== undefined) {
      result[key] = cloneValue(schema[key]);
    }
  }

  if (schema.properties && typeof schema.properties === 'object') {
    result.properties = {};
    for (const [name, propertySchema] of Object.entries(schema.properties)) {
      result.properties[name] = normalizeSchema(propertySchema);
    }
  }

  if (schema.items) {
    result.items = normalizeSchema(schema.items);
  }

  if (Array.isArray(schema.required)) {
    result.required = [...schema.required];
  }

  if (!result.type) {
    if (result.properties) {
      result.type = 'object';
    } else if (result.items) {
      result.type = 'array';
    } else {
      result.type = 'string';
    }
  }

  return result;
}

function applySchemaToArg(arg: ToolArg, schema: any) {
  const normalized = normalizeSchema(schema);
  arg.schema = normalized;
  arg.type = normalized.type;
  if (!arg.description && normalized.description) {
    arg.description = normalized.description;
  }
  if (Array.isArray(normalized.enum)) {
    arg.enum = normalized.enum;
  }
  if (normalized.items) {
    arg.items = normalized.items;
  }
  if (normalized.properties) {
    arg.properties = normalized.properties;
  }
}

function isComplexArg(arg?: ToolArg) {
  const type = arg?.schema?.type || arg?.type;
  return type === 'object' || type === 'array';
}

/**
 * Extract tools from an OpenAPI 3.x document.
 * Ported from console-ui/src/pages/AI/McpDetail/Swagger2Tools.js
 */
export function extractToolsFromOpenAPI(openapi: any): McpConfig {
  const mcpConfig: McpConfig = {
    server: { name: 'openapi-server', securitySchemes: [] },
    tools: [],
  };

  // Process security schemes
  if (openapi.components?.securitySchemes) {
    const schemes = openapi.components.securitySchemes;
    for (const name of Object.keys(schemes)) {
      const scheme = schemes[name];
      mcpConfig.server.securitySchemes.push({
        id: name,
        type: scheme.type,
        scheme: scheme.scheme,
        in: scheme.in,
        name: scheme.name,
      });
    }
    mcpConfig.server.securitySchemes.sort((a: any, b: any) => a.id.localeCompare(b.id));
  }

  // Process paths
  if (openapi.paths) {
    for (const path of Object.keys(openapi.paths)) {
      const pathItem = openapi.paths[path];
      const methods = ['get', 'post', 'put', 'delete', 'patch', 'options', 'head', 'trace'];
      for (const method of methods) {
        if (pathItem[method]) {
          const tool = convertOperation(path, method, pathItem[method], openapi.servers);
          tool.responseTemplate = createResponseTemplate(pathItem[method]);
          mcpConfig.tools.push(tool);
        }
      }
    }
  }

  mcpConfig.tools.sort((a, b) => a.name.localeCompare(b.name));
  return mcpConfig;
}

function convertOperation(path: string, method: string, operation: any, servers?: any[]): RawTool {
  let toolName = operation.operationId || '';
  if (!toolName) {
    const pathName = path.replace(/[{}]/g, '').replace(/\//g, '_');
    toolName = method.toLowerCase() + pathName.charAt(0).toUpperCase() + pathName.slice(1);
  }

  const tool: RawTool = {
    name: toolName,
    description: operation.summary || '',
    args: [],
    requestTemplate: createRequestTemplate(path, method, operation, servers),
    responseTemplate: {},
  };

  // Operation-level security
  if (operation.security?.length > 0) {
    const schemeNames = Object.keys(operation.security[0]);
    if (schemeNames.length > 0) {
      tool.requestTemplate.security = { id: schemeNames[0] };
    }
  }

  // Parameters -> args
  if (operation.parameters) {
    for (const param of operation.parameters) {
      const arg: ToolArg = {
        name: param.name,
        description: param.description || '',
        required: param.required || false,
        position: param.in,
      };
      if (param.schema) {
        applySchemaToArg(arg, param.schema);
      }
      tool.args.push(arg);
    }
  }

  // RequestBody -> args
  if (operation.requestBody?.content) {
    for (const contentType of Object.keys(operation.requestBody.content)) {
      const mediaType = operation.requestBody.content[contentType];
      if (!mediaType.schema) continue;
      const schema = mediaType.schema;

      if (
        (contentType.includes('application/json') ||
          contentType.includes('application/x-www-form-urlencoded')) &&
        schema.type === 'object' &&
        schema.properties
      ) {
        for (const propName of Object.keys(schema.properties)) {
          const prop = schema.properties[propName];
          if (!prop) continue;
          const arg: ToolArg = {
            name: propName,
            description: prop.description || '',
            type: prop.type,
            required: schema.required?.includes(propName) || false,
            position: 'body',
          };
          applySchemaToArg(arg, prop);
          tool.args.push(arg);
        }
      }
      break; // only first content type
    }
  }

  tool.args.sort((a, b) => a.name.localeCompare(b.name));
  return tool;
}

function createRequestTemplate(path: string, method: string, operation: any, servers?: any[]) {
  let serverURL = servers?.[0]?.url || '';
  if (typeof serverURL === 'string') {
    serverURL = serverURL.trim().replace(/\/+$/, '');
  }
  const normalizedPath = typeof path === 'string' ? `/${path}`.replace(/\/{2,}/g, '/').replace(/^\/+/, '/') : '';
  const fullUrl = serverURL + normalizedPath;

  const template: any = {
    url: fullUrl,
    method: method.toUpperCase(),
    headers: [] as { key: string; value: string }[],
  };

  if (operation.security?.length > 0) {
    for (const req of operation.security) {
      for (const sn of Object.keys(req)) {
        template.security = { id: sn };
        break;
      }
      break;
    }
  }

  if (operation.requestBody?.content) {
    for (const ct of Object.keys(operation.requestBody.content)) {
      template.headers.push({ key: 'Content-Type', value: ct });
      break;
    }
  }

  return template;
}

function createResponseTemplate(operation: any) {
  let successResponse: any = null;
  if (operation.responses) {
    for (const [code, resp] of Object.entries(operation.responses)) {
      if (code.startsWith('2') && resp) {
        successResponse = resp;
        break;
      }
    }
  }

  if (!successResponse || !successResponse.content || Object.keys(successResponse.content).length === 0) {
    return {};
  }

  const template: any = {
    prependBody:
      '# API Response Information\n\n' +
      "Below is the response from an API call. To help you understand the data, I've provided:\n\n" +
      '1. A detailed description of all fields in the response structure\n' +
      '2. The complete API response\n\n' +
      '## Response Structure\n\n',
  };

  for (const [contentType, mediaType] of Object.entries(successResponse.content) as [string, any][]) {
    if (!mediaType.schema) continue;
    template.prependBody += `> Content-Type: ${contentType}\n\n`;
    const schema = mediaType.schema;
    if (schema.type === 'array' && schema.items) {
      template.prependBody += '- **items**: Array of items (Type: array)\n';
      processSchemaProperties(template, schema.items, 'items', 1, 10);
    } else if (schema.type === 'object' && schema.properties) {
      for (const pn of Object.keys(schema.properties).sort()) {
        const pr = schema.properties[pn];
        if (!pr) continue;
        template.prependBody += `- **${pn}**: ${pr.description || ''}`;
        if (pr.type) template.prependBody += ` (Type: ${pr.type})`;
        template.prependBody += '\n';
        processSchemaProperties(template, pr, pn, 1, 10);
      }
    }
  }

  template.prependBody += '\n## Original Response\n\n';
  return template;
}

function processSchemaProperties(template: any, schema: any, path: string, depth: number, maxDepth: number) {
  if (depth > maxDepth) return;
  const indent = '  '.repeat(depth);

  if (schema.type === 'array' && schema.items) {
    const items = schema.items;
    if (items.type === 'object' && items.properties) {
      for (const pn of Object.keys(items.properties).sort()) {
        const pr = items.properties[pn];
        if (!pr) continue;
        const pp = `${path}[][${pn}]`;
        template.prependBody += `${indent}- **${pp}**: ${pr.description || ''}`;
        if (pr.type) template.prependBody += ` (Type: ${pr.type})`;
        template.prependBody += '\n';
        processSchemaProperties(template, pr, pp, depth + 1, maxDepth);
      }
    } else if (items.type) {
      template.prependBody += `${indent}- **${path}[]**: Items of type ${items.type}\n`;
    }
    return;
  }

  if (schema.type === 'object' && schema.properties) {
    for (const pn of Object.keys(schema.properties).sort()) {
      const pr = schema.properties[pn];
      if (!pr) continue;
      const pp = `${path}.${pn}`;
      template.prependBody += `${indent}- **${pp}**: ${pr.description || ''}`;
      if (pr.type) template.prependBody += ` (Type: ${pr.type})`;
      template.prependBody += '\n';
      processSchemaProperties(template, pr, pp, depth + 1, maxDepth);
    }
  }
}

/**
 * Transform extracted OpenAPI config into MCP toolSpecification format.
 * Ported from OpenApiService.js transformToolsFromConfig()
 */
export function transformToolsFromConfig(config: McpConfig) {
  const securitySchemes = config.server?.securitySchemes || [];

  const toolsMeta: Record<string, any> = {};
  for (const tool of config.tools) {
    const argsPosition: Record<string, string> = {};
    for (const arg of tool.args) {
      argsPosition[arg.name] = arg.position;
    }
    toolsMeta[tool.name] = {
      enabled: true,
      templates: {
        'json-go-template': {
          responseTemplate: tool.responseTemplate,
          requestTemplate: { ...tool.requestTemplate },
          argsPosition,
        },
      },
    };
  }

  const tools = config.tools.map((tool) => ({
    name: tool.name,
    description: tool.description,
    inputSchema: {
      type: 'object' as const,
      properties: tool.args.reduce(
        (acc, arg) => {
          const argSchema = { ...(arg.schema || { type: arg.type || 'string' }) };
          if (arg.description) {
            argSchema.description = arg.description;
          }
          if (arg.enum && !argSchema.enum) {
            argSchema.enum = arg.enum;
          }
          if (arg.items && !argSchema.items) {
            argSchema.items = arg.items;
          }
          if (arg.properties && !argSchema.properties) {
            argSchema.properties = arg.properties;
          }
          acc[arg.name] = argSchema;
          return acc;
        },
        {} as Record<string, any>
      ),
      required: tool.args.filter((a) => a.required).map((a) => a.name),
    },
  }));

  // Process argsPosition -> requestTemplate mapping
  try {
    const toolArgsByName: Record<string, ToolArg[]> = {};
    for (const t of config.tools) {
      toolArgsByName[t.name] = t.args || [];
    }

    const ensureHeadersArray = (headers: any): { key: string; value: string }[] => {
      if (!headers) return [];
      if (Array.isArray(headers)) return headers;
      if (typeof headers === 'object') {
        return Object.entries(headers).map(([k, v]) => ({ key: k, value: String(v) }));
      }
      return [];
    };

    const hasHeaderKey = (headers: { key: string; value: string }[], key: string) =>
      headers.some((h) => (h.key || '').toLowerCase() === key.toLowerCase());

    const getContentType = (headers: { key: string; value: string }[]) => {
      const h = headers.find((it) => (it.key || '').toLowerCase() === 'content-type');
      return h ? h.value.toLowerCase() : '';
    };

    for (const toolName of Object.keys(toolsMeta)) {
      const meta = toolsMeta[toolName];
      const tmpl = meta?.templates?.['json-go-template'];
      if (!tmpl?.requestTemplate) continue;

      const argsPos: Record<string, string> = tmpl.argsPosition || {};
      let url: string = tmpl.requestTemplate.url || '';
      const headers = ensureHeadersArray(tmpl.requestTemplate.headers);
      let body: string | undefined = tmpl.requestTemplate.body;

      const allArgs = toolArgsByName[toolName] || [];
      const byName: Record<string, ToolArg> = {};
      for (const a of allArgs) byName[a.name] = a;

      const entries = Object.entries(argsPos);
      const pathArgs = entries.filter(([, pos]) => pos === 'path').map(([n]) => n);
      const queryArgs = entries.filter(([, pos]) => pos === 'query').map(([n]) => n);
      const headerArgs = entries.filter(([, pos]) => pos === 'header').map(([n]) => n);
      const cookieArgs = entries.filter(([, pos]) => pos === 'cookie').map(([n]) => n);
      const bodyArgs = entries.filter(([, pos]) => pos === 'body').map(([n]) => n);

      let shouldKeepArgsPosition = false;
      const totalArgsCount = entries.length;
      const allInQuery = totalArgsCount > 0 && queryArgs.length === totalArgsCount;
      const allInBody = totalArgsCount > 0 && bodyArgs.length === totalArgsCount;
      const hasComplexQueryArg = queryArgs.some((n) => isComplexArg(byName[n]));
      const hasComplexBodyArg = bodyArgs.some((n) => isComplexArg(byName[n]));

      // path placeholders
      for (const name of pathArgs) {
        const re = new RegExp('\\{' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\}', 'g');
        url = url.replace(re, `{{.args.${name}}}`);
      }

      // query params
      if (allInQuery) {
        if (hasComplexQueryArg) {
          shouldKeepArgsPosition = true;
        } else {
          tmpl.requestTemplate.argsToUrlParam = true;
        }
      } else if (queryArgs.length > 0) {
        const pairs = queryArgs.map((name) => `${name}={{.args.${name}}}`);
        const connector = url.includes('?') ? '&' : '?';
        url = url + connector + pairs.join('&');
      }

      // header params
      for (const name of headerArgs) {
        if (!hasHeaderKey(headers, name)) {
          headers.push({ key: name, value: `{{.args.${name}}}` });
        }
      }

      // cookie params
      if (cookieArgs.length > 0) {
        const cookiePairs = cookieArgs.map((name) => `${name}={{.args.${name}}}`);
        const cookieValue = cookiePairs.join('; ');
        const idx = headers.findIndex((h) => (h.key || '').toLowerCase() === 'cookie');
        if (idx >= 0) {
          headers[idx].value = headers[idx].value ? `${headers[idx].value}; ${cookieValue}` : cookieValue;
        } else {
          headers.push({ key: 'Cookie', value: cookieValue });
        }
      }

      // body params
      const hasExplicit =
        body !== undefined ||
        tmpl.requestTemplate.argsToJsonBody === true ||
        tmpl.requestTemplate.argsToFormBody === true ||
        tmpl.requestTemplate.argsToUrlParam === true;

      if (bodyArgs.length > 0) {
        const ct = getContentType(headers);
        if (allInBody) {
          if (ct.includes('application/x-www-form-urlencoded') || ct.includes('multipart/form-data')) {
            tmpl.requestTemplate.argsToFormBody = true;
          } else {
            tmpl.requestTemplate.argsToJsonBody = true;
            if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
              headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
            }
          }
          if (hasComplexBodyArg) {
            shouldKeepArgsPosition = true;
          }
        } else if (!hasExplicit) {
          if (ct.includes('application/x-www-form-urlencoded')) {
            const formPairs = bodyArgs.map((name) => `${name}={{.args.${name}}}`);
            body = formPairs.join('&');
          } else {
            if (hasComplexBodyArg) {
              tmpl.requestTemplate.argsToJsonBody = true;
              shouldKeepArgsPosition = true;
              if (!hasHeaderKey(headers, 'Content-Type')) {
                headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
              }
            } else {
              const jsonPairs = bodyArgs.map((name) => {
                const a = byName[name];
                const isString = a?.type === 'string';
                const valueTpl = isString ? `"{{.args.${name}}}"` : `{{.args.${name}}}`;
                return `  "${name}": ${valueTpl}`;
              });
              body = `{\n${jsonPairs.join(',\n')}\n}`;
              if (!hasHeaderKey(headers, 'Content-Type')) {
                headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
              }
            }
          }
        }
      }

      tmpl.requestTemplate.url = url;
      if (headers.length > 0) tmpl.requestTemplate.headers = headers;
      if (body !== undefined) {
        tmpl.requestTemplate.body = body;
        delete tmpl.requestTemplate.argsToJsonBody;
        delete tmpl.requestTemplate.argsToUrlParam;
        delete tmpl.requestTemplate.argsToFormBody;
      } else {
        const ct2 = getContentType(headers);
        if (!allInBody && bodyArgs.length > 0 && ct2.includes('application/x-www-form-urlencoded')) {
          tmpl.requestTemplate.argsToFormBody = true;
          shouldKeepArgsPosition = true;
        }
      }
      if (!shouldKeepArgsPosition) {
        delete tmpl.argsPosition;
      }
    }
  } catch (e) {
    console.warn('argsPosition to requestTemplate transform failed:', e);
  }

  return { tools, toolsMeta, securitySchemes };
}
