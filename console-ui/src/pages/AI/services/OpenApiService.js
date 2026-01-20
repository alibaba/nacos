
import swagger2openapi from 'swagger2openapi';
import YAML from 'js-yaml';

// Helper function to resolve $ref references
const resolveRefs = (obj, root, visited = new Set()) => {
    if (!obj || typeof obj !== 'object') {
        return obj;
    }

    // Handle arrays
    if (Array.isArray(obj)) {
        return obj.map(item => resolveRefs(item, root, visited));
    }

    // Handle $ref references
    if (obj.$ref && typeof obj.$ref === 'string') {
        // Check for circular references
        if (visited.has(obj.$ref)) {
            console.warn('Circular reference detected:', obj.$ref);
            return { error: 'Circular reference detected' };
        }

        // Parse reference path
        const refPath = obj.$ref;

        // Handle internal references (#/components/schemas/xxx)
        if (refPath.startsWith('#/')) {
            const pathParts = refPath.substring(2).split('/');
            let refObj = root;

            for (const part of pathParts) {
                if (refObj && typeof refObj === 'object' && refObj[part] !== undefined) {
                    refObj = refObj[part];
                } else {
                    console.warn('Unable to resolve reference path:', refPath);
                    return obj; // Return original reference to avoid data corruption
                }
            }

            // Recursively resolve referenced object, add to visited set
            visited.add(refPath);
            const resolved = resolveRefs(refObj, root, new Set(visited));
            visited.delete(refPath);
            return resolved;
        }

        // For other types of references, temporarily return original object
        console.warn('Unsupported reference type:', refPath);
        return obj;
    }

    // Recursively process all properties of object
    const result = {};
    for (const [key, value] of Object.entries(obj)) {
        result[key] = resolveRefs(value, root, visited);
    }
    return result;
};

// Validate format and parse OpenAPI
export const parseOpenAPI = async content => {
    try {
        // Automatically detect JSON/YAML format
        let parsedContent;
        try {
            parsedContent = JSON.parse(content);
        } catch (jsonError) {
            // Try YAML parsing
            try {
                parsedContent = YAML.load(content);
            } catch (yamlError) {
                throw new Error('Invalid JSON/YAML format');
            }
        }
        parsedContent = resolveRefs(parsedContent, parsedContent);
        if (parsedContent.swagger) {
            const converted = await swagger2openapi.convertObj(parsedContent, {});
            return converted.openapi;
        }

        // Validate OpenAPI 3.x document
        if (parsedContent.openapi) {
            // More validation logic can be added
            return parsedContent;
        }
    } catch (e) {
        console.error('Parse failed:', e);
        throw new Error('File format invalid');
    }
};

// Extract tools logic from OpenAPI (previously in Swagger2Tools.js, but ShowTools.js also has partial conversion logic)
// The logic here is mainly the complex conversion part of handleConfirm in ShowTools.js
export const transformToolsFromConfig = (config) => {
    // Extract top-level securitySchemes from OpenAPI
    const securitySchemes = Array.isArray(config?.server?.securitySchemes)
        ? config.server.securitySchemes
        : [];

    const toolsMeta = config.tools.reduce((acc, tool) => {
        const argsPosition = tool.args.reduce((acc, arg) => {
            acc[arg.name] = arg.position;
            return acc;
        }, {});
        acc[tool.name] = {
            enabled: true,
            templates: {
                'json-go-template': {
                    responseTemplate: tool.responseTemplate,
                    requestTemplate: tool.requestTemplate,
                    argsPosition,
                },
            },
        };
        return acc;
    }, {});

    const tools = config.tools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: {
            type: 'object',
            properties: tool.args.reduce((acc, arg) => {
                acc[arg.name] = {
                    type: arg.type,
                    description: arg.description,
                    properties: arg.properties,
                };
                return acc;
            }, {}),
            required: tool.args.filter(arg => arg.required).map(arg => arg.name),
        },
    }));

    // Before generating final specification: merge argsPosition into requestTemplate
    try {
        // Build a quick index: toolName -> args array (with type, position)
        const toolArgsByName = config.tools.reduce((acc, t) => {
            acc[t.name] = t.args || [];
            return acc;
        }, {});

        const ensureHeadersArray = headers => {
            // Normalize headers to array format [{key, value}, ...]
            if (!headers) return [];
            if (Array.isArray(headers)) return headers;
            if (typeof headers === 'object') {
                return Object.entries(headers).map(([k, v]) => ({ key: k, value: String(v) }));
            }
            return [];
        };

        const hasHeaderKey = (headers, key) => {
            return headers.some(h => (h.key || '').toLowerCase() === String(key).toLowerCase());
        };

        const getContentType = headers => {
            const h = headers.find(it => (it.key || '').toLowerCase() === 'content-type');
            return h ? String(h.value).toLowerCase() : '';
        };

        Object.keys(toolsMeta || {}).forEach(toolName => {
            const meta = toolsMeta[toolName];
            const tmpl = meta?.templates?.['json-go-template'];
            if (!tmpl || !tmpl.requestTemplate) return;

            const argsPos = tmpl.argsPosition || {};
            let url = tmpl.requestTemplate.url || '';
            let headers = ensureHeadersArray(tmpl.requestTemplate.headers);
            let body = tmpl.requestTemplate.body; // May be string or object, keep as is priority

            // Collect parameter names by type
            const allArgs = toolArgsByName[toolName] || [];
            const byName = allArgs.reduce((acc, a) => {
                acc[a.name] = a;
                return acc;
            }, {});

            const entries = Object.entries(argsPos);
            const pathArgs = entries.filter(([, pos]) => pos === 'path').map(([n]) => n);
            const queryArgs = entries.filter(([, pos]) => pos === 'query').map(([n]) => n);
            const headerArgs = entries.filter(([, pos]) => pos === 'header').map(([n]) => n);
            const cookieArgs = entries.filter(([, pos]) => pos === 'cookie').map(([n]) => n);
            const bodyArgs = entries.filter(([, pos]) => pos === 'body').map(([n]) => n);

            // Mark whether to keep argsPosition (needed when depending on argsTo* flags)
            let shouldKeepArgsPosition = false;

            // 1) Handle path placeholders: replace {name} with {{urlqueryescape .args.name}}
            pathArgs.forEach(name => {
                const re = new RegExp(
                    '\\{' + name.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\$&') + '\\}',
                    'g'
                );
                // Don't use template function, directly insert placeholder {{.args.name}}
                url = url.replace(re, `{{.args.${name}}}`);
            });

            // Count overall positions
            const totalArgsCount = entries.length;
            const allInQuery = totalArgsCount > 0 && queryArgs.length === totalArgsCount;
            const allInBody = totalArgsCount > 0 && bodyArgs.length === totalArgsCount;

            // 2) Handle query: when all in query, use argsToUrlParam flag, don't append to URL
            if (allInQuery) {
                tmpl.requestTemplate.argsToUrlParam = true;
            } else if (queryArgs.length > 0) {
                // In mixed scenarios, still append query parameters to URL
                const pairs = queryArgs.map(name => `${name}={{.args.${name}}}`);
                const connector = url.includes('?') ? '&' : '?';
                url = url + (pairs.length > 0 ? connector + pairs.join('&') : '');
            }

            // 3) Handle header: add header entry for each header parameter
            if (headerArgs.length > 0) {
                headerArgs.forEach(name => {
                    if (!hasHeaderKey(headers, name)) {
                        // Don't use toString, directly use placeholder
                        headers.push({ key: name, value: `{{.args.${name}}}` });
                    }
                });
            }

            // 4) Handle cookie: merge all cookie parameters into one Cookie header
            if (cookieArgs.length > 0) {
                const cookiePairs = cookieArgs.map(name => `${name}={{.args.${name}}}`);
                const cookieValue = cookiePairs.join('; ');
                const idx = headers.findIndex(h => (h.key || '').toLowerCase() === 'cookie');
                if (idx >= 0) {
                    headers[idx].value = headers[idx].value
                        ? `${headers[idx].value}; ${cookieValue}`
                        : cookieValue;
                } else {
                    headers.push({ key: 'Cookie', value: cookieValue });
                }
            }

            // 5) Handle body:
            //    - If all in body: set argsToJsonBody/argsToFormBody based on Content-Type, don't generate body directly
            //    - Otherwise (mixed scenario): if body/argsTo* not explicitly provided, generate based on Content-Type
            const hasExplicit =
                body !== undefined ||
                tmpl.requestTemplate.argsToJsonBody === true ||
                tmpl.requestTemplate.argsToFormBody === true ||
                tmpl.requestTemplate.argsToUrlParam === true;

            if (bodyArgs.length > 0) {
                const ct = getContentType(headers);
                if (allInBody) {
                    // All in body: control through flags
                    if (
                        ct.includes('application/x-www-form-urlencoded') ||
                        ct.includes('multipart/form-data')
                    ) {
                        tmpl.requestTemplate.argsToFormBody = true;
                    } else {
                        tmpl.requestTemplate.argsToJsonBody = true;
                        if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                            headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                        }
                    }
                } else if (!hasExplicit) {
                    // Mixed scenario and not explicitly specified: maintain original auto-generation strategy
                    if (ct.includes('application/x-www-form-urlencoded')) {
                        const formPairs = bodyArgs.map(name => `${name}={{.args.${name}}}`);
                        body = formPairs.join('&');
                    } else {
                        const hasComplex = bodyArgs.some(n => {
                            const a = byName[n];
                            const t = a && (a.type || (a.schema && a.schema.type));
                            return t === 'object' || t === 'array';
                        });

                        if (hasComplex) {
                            tmpl.requestTemplate.argsToJsonBody = true;
                            shouldKeepArgsPosition = true;
                            if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                                headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                            }
                        } else {
                            const jsonPairs = bodyArgs.map(name => {
                                const a = byName[name];
                                const t = a && (a.type || (a.schema && a.schema.type));
                                const isString = t === 'string';
                                const valueTpl = isString ? `"{{.args.${name}}}"` : `{{.args.${name}}}`;
                                return `  \"${name}\": ${valueTpl}`;
                            });
                            body = `{$\n${jsonPairs.join(',\n')}\n}`.replace('{$\n', '{\n');
                            if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                                headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                            }
                        }
                    }
                }
            }

            // Write back to template and remove argsPosition field
            tmpl.requestTemplate.url = url;
            if (headers.length > 0) {
                tmpl.requestTemplate.headers = headers;
            }
            if (body !== undefined) {
                tmpl.requestTemplate.body = body;
                // When explicit body is generated, remove flags (avoid conflicts)
                delete tmpl.requestTemplate.argsToJsonBody;
                delete tmpl.requestTemplate.argsToUrlParam;
                delete tmpl.requestTemplate.argsToFormBody;
            } else {
                // No explicit body generated, but bodyArgs exist and Content-Type is form, set form flag
                const ct2 = getContentType(headers);
                if (!allInBody) {
                    if (bodyArgs.length > 0 && ct2.includes('application/x-www-form-urlencoded')) {
                        tmpl.requestTemplate.argsToFormBody = true;
                        shouldKeepArgsPosition = true;
                    }
                }
            }
            // Only delete argsPosition when not depending on flags;
            // If all in query/body already controlled by flags, can also delete
            if (!shouldKeepArgsPosition || allInQuery || allInBody) {
                delete tmpl.argsPosition;
            }
        });
    } catch (e) {
        // Conversion failure does not affect import process, only log
        console.warn('argsPosition to requestTemplate transform failed:', e);
    }

    return {
        tools,
        toolsMeta,
        securitySchemes,
    };
}
