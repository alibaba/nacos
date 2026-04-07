/* eslint-disable @typescript-eslint/no-explicit-any */
import YAML from 'js-yaml';
import swagger2openapi from 'swagger2openapi';

/**
 * Resolve $ref references in an OpenAPI document
 */
function resolveRefs(obj: any, root: any, visited: Set<string> = new Set()): any {
  if (!obj || typeof obj !== 'object') return obj;

  if (Array.isArray(obj)) {
    return obj.map((item) => resolveRefs(item, root, visited));
  }

  if (obj.$ref && typeof obj.$ref === 'string') {
    if (visited.has(obj.$ref)) {
      return { error: 'Circular reference detected' };
    }

    const refPath: string = obj.$ref;

    if (refPath.startsWith('#/')) {
      const pathParts = refPath.substring(2).split('/');
      let refObj: any = root;

      for (const part of pathParts) {
        if (refObj && typeof refObj === 'object' && refObj[part] !== undefined) {
          refObj = refObj[part];
        } else {
          return obj;
        }
      }

      visited.add(refPath);
      const resolved = resolveRefs(refObj, root, new Set(visited));
      visited.delete(refPath);
      return resolved;
    }

    return obj;
  }

  const result: Record<string, any> = {};
  for (const [key, value] of Object.entries(obj)) {
    result[key] = resolveRefs(value, root, visited);
  }
  return result;
}

/**
 * Parse OpenAPI/Swagger content (JSON or YAML) and return an OpenAPI 3.x document.
 */
export async function parseOpenAPI(content: string): Promise<any> {
  let parsed: any;

  try {
    parsed = JSON.parse(content);
  } catch {
    try {
      parsed = YAML.load(content);
    } catch {
      throw new Error('Invalid JSON/YAML format');
    }
  }

  parsed = resolveRefs(parsed, parsed);

  // Swagger 2.x -> OpenAPI 3.x
  if (parsed.swagger) {
    const converted = await swagger2openapi.convertObj(parsed, {});
    return converted.openapi;
  }

  if (parsed.openapi) {
    return parsed;
  }

  throw new Error('File format invalid: not a valid OpenAPI or Swagger document');
}
