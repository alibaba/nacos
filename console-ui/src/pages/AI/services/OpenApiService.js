
import swagger2openapi from 'swagger2openapi';
import YAML from 'js-yaml';

// 解析 $ref 引用的辅助函数
const resolveRefs = (obj, root, visited = new Set()) => {
    if (!obj || typeof obj !== 'object') {
        return obj;
    }

    // 处理数组
    if (Array.isArray(obj)) {
        return obj.map(item => resolveRefs(item, root, visited));
    }

    // 处理 $ref 引用
    if (obj.$ref && typeof obj.$ref === 'string') {
        // 检查循环引用
        if (visited.has(obj.$ref)) {
            console.warn('检测到循环引用:', obj.$ref);
            return { error: 'Circular reference detected' };
        }

        // 解析引用路径
        const refPath = obj.$ref;

        // 处理内部引用 (#/components/schemas/xxx)
        if (refPath.startsWith('#/')) {
            const pathParts = refPath.substring(2).split('/');
            let refObj = root;

            for (const part of pathParts) {
                if (refObj && typeof refObj === 'object' && refObj[part] !== undefined) {
                    refObj = refObj[part];
                } else {
                    console.warn('无法解析引用路径:', refPath);
                    return obj; // 返回原始引用，避免破坏数据
                }
            }

            // 递归解析引用的对象，添加到访问记录中
            visited.add(refPath);
            const resolved = resolveRefs(refObj, root, new Set(visited));
            visited.delete(refPath);
            return resolved;
        }

        // 其他类型的引用暂时返回原始对象
        console.warn('不支持的引用类型:', refPath);
        return obj;
    }

    // 递归处理对象的所有属性
    const result = {};
    for (const [key, value] of Object.entries(obj)) {
        result[key] = resolveRefs(value, root, visited);
    }
    return result;
};

// 校验格式并解析 OpenAPI
export const parseOpenAPI = async content => {
    try {
        // 自动识别 JSON/YAML 格式
        let parsedContent;
        try {
            parsedContent = JSON.parse(content);
        } catch (jsonError) {
            // 尝试 YAML 解析
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

        // 验证 OpenAPI 3.x 文档
        if (parsedContent.openapi) {
            // 可以添加更多验证逻辑
            return parsedContent;
        }
    } catch (e) {
        console.error('解析失败:', e);
        throw new Error('File format invalid');
    }
};

// 从 OpenAPI 提取工具逻辑 (之前在 Swagger2Tools.js 中, 但 ShowTools.js 也有部分转换逻辑)
// 这里的逻辑主要是 ShowTools.js 中 handleConfirm 部分的复杂转换
export const transformToolsFromConfig = (config) => {
    // 提取 OpenAPI 顶层的 securitySchemes
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

    // 在生成最终 specification 之前：将 argsPosition 合并进 requestTemplate
    try {
        // 建立一个快速索引：toolName -> args 数组（含类型、position）
        const toolArgsByName = config.tools.reduce((acc, t) => {
            acc[t.name] = t.args || [];
            return acc;
        }, {});

        const ensureHeadersArray = headers => {
            // 规范化 headers 为数组 [{key, value}, ...]
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
            let body = tmpl.requestTemplate.body; // 可能为字符串或对象，保留原样优先

            // 收集各类参数名
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

            // 标记是否需要保留 argsPosition（当依赖 argsTo* flags 时需要）
            let shouldKeepArgsPosition = false;

            // 1) 处理 path 占位：将 {name} 替换为 {{urlqueryescape .args.name}}
            pathArgs.forEach(name => {
                const re = new RegExp(
                    '\\{' + name.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\$&') + '\\}',
                    'g'
                );
                // 不使用模板函数，直接插入占位 {{.args.name}}
                url = url.replace(re, `{{.args.${name}}}`);
            });

            // 统计总体位置
            const totalArgsCount = entries.length;
            const allInQuery = totalArgsCount > 0 && queryArgs.length === totalArgsCount;
            const allInBody = totalArgsCount > 0 && bodyArgs.length === totalArgsCount;

            // 2) 处理 query：当全部在 query 时，使用 argsToUrlParam 标记，不拼接到 URL
            if (allInQuery) {
                tmpl.requestTemplate.argsToUrlParam = true;
            } else if (queryArgs.length > 0) {
                // 混合场景下仍然把 query 参数拼接到 URL
                const pairs = queryArgs.map(name => `${name}={{.args.${name}}}`);
                const connector = url.includes('?') ? '&' : '?';
                url = url + (pairs.length > 0 ? connector + pairs.join('&') : '');
            }

            // 3) 处理 header：为每个 header 参数添加 header 条目
            if (headerArgs.length > 0) {
                headerArgs.forEach(name => {
                    if (!hasHeaderKey(headers, name)) {
                        // 不使用 toString，直接占位
                        headers.push({ key: name, value: `{{.args.${name}}}` });
                    }
                });
            }

            // 4) 处理 cookie：将所有 cookie 参数合并为一个 Cookie 头
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

            // 5) 处理 body：
            //    - 如果全部在 body：根据 Content-Type 设置 argsToJsonBody/argsToFormBody，不直接生成 body
            //    - 否则（混合场景）：若未显式提供 body/argsTo*，再根据 Content-Type 生成
            const hasExplicit =
                body !== undefined ||
                tmpl.requestTemplate.argsToJsonBody === true ||
                tmpl.requestTemplate.argsToFormBody === true ||
                tmpl.requestTemplate.argsToUrlParam === true;

            if (bodyArgs.length > 0) {
                const ct = getContentType(headers);
                if (allInBody) {
                    // 全部在 body：通过标记控制
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
                    // 混合场景且未显式指定：保持原有自动生成策略
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

            // 写回模板，并移除 argsPosition 字段
            tmpl.requestTemplate.url = url;
            if (headers.length > 0) {
                tmpl.requestTemplate.headers = headers;
            }
            if (body !== undefined) {
                tmpl.requestTemplate.body = body;
                // 当生成了明确的 body 时，移除 flags（避免冲突）
                delete tmpl.requestTemplate.argsToJsonBody;
                delete tmpl.requestTemplate.argsToUrlParam;
                delete tmpl.requestTemplate.argsToFormBody;
            } else {
                // 未生成明确 body，但存在 bodyArgs 且 Content-Type 为表单时，设置表单标记
                const ct2 = getContentType(headers);
                if (!allInBody) {
                    if (bodyArgs.length > 0 && ct2.includes('application/x-www-form-urlencoded')) {
                        tmpl.requestTemplate.argsToFormBody = true;
                        shouldKeepArgsPosition = true;
                    }
                }
            }
            // 仅在不依赖 flags 的情况下删除 argsPosition；
            // 若全部在 query/body 已由 flags 控制，也可删除
            if (!shouldKeepArgsPosition || allInQuery || allInBody) {
                delete tmpl.argsPosition;
            }
        });
    } catch (e) {
        // 转换失败不影响导入流程，仅记录日志
        console.warn('argsPosition to requestTemplate transform failed:', e);
    }

    return {
        tools,
        toolsMeta,
        securitySchemes,
    };
}
