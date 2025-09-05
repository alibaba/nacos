/**
 * Extract tools from OpenAPI document
 * @returns {object} MCP config object
 * @param openapi
 */
export function extractToolsFromOpenAPI(openapi) {
  const mcpConfig = {
    server: {
      name: 'openapi-server',
      securitySchemes: [],
    },
    tools: [],
  };

  // Process security schemes
  if (openapi.components && openapi.components.securitySchemes) {
    const securitySchemes = openapi.components.securitySchemes;
    for (const name in securitySchemes) {
      const scheme = securitySchemes[name];
      mcpConfig.server.securitySchemes.push({
        id: name,
        type: scheme.type,
        scheme: scheme.scheme,
        in: scheme.in,
        name: scheme.name,
      });
    }
    // Sort security schemes by ID for consistent output
    mcpConfig.server.securitySchemes.sort((a, b) => a.id.localeCompare(b.id));
  }

  // Process paths and operations
  if (openapi.paths) {
    for (const path in openapi.paths) {
      const pathItem = openapi.paths[path];
      const operations = getOperations(pathItem);
      for (const method in operations) {
        const operation = operations[method];
        const tool = convertOperation(path, method, operation, openapi.servers);
        // Create response template
        tool.responseTemplate = createResponseTemplate(operation);
        mcpConfig.tools.push(tool);
      }
    }
  }
  // Sort tools by name for consistent output
  mcpConfig.tools.sort((a, b) => a.name.localeCompare(b.name));

  return mcpConfig;
}

// 保持其余函数不变
function getOperations(pathItem) {
  const operations = {};

  if (pathItem.get) operations.get = pathItem.get;
  if (pathItem.post) operations.post = pathItem.post;
  if (pathItem.put) operations.put = pathItem.put;
  if (pathItem.delete) operations.delete = pathItem.delete;
  if (pathItem.options) operations.options = pathItem.options;
  if (pathItem.head) operations.head = pathItem.head;
  if (pathItem.patch) operations.patch = pathItem.patch;
  if (pathItem.trace) operations.trace = pathItem.trace;

  return operations;
}

function convertOperation(path, method, operation, servers) {
  // Generate a tool name
  let toolName = operation.operationId || '';
  if (!toolName) {
    const pathName = path.replace(/[{}]/g, '').replace(/\//g, '_');
    toolName = method.toLowerCase() + pathName.charAt(0).toUpperCase() + pathName.slice(1);
  }

  // Create the tool
  const tool = {
    name: toolName,
    description: operation.summary || '',
    args: [],
    requestTemplate: createRequestTemplate(path, method, operation, servers),
  };

  // Convert parameters to arguments
  // Process operation-level security requirements
  if (operation.security && operation.security.length > 0) {
    // Take the first security requirement
    const securityRequirement = operation.security[0];
    // Take the first scheme from that requirement
    const schemeNames = Object.keys(securityRequirement);
    if (schemeNames.length > 0) {
      tool.requestTemplate.security = {
        id: schemeNames[0],
      };
    }
  }

  // Convert parameters to arguments
  if (operation.parameters) {
    tool.args = operation.parameters.map(param => {
      const arg = {
        name: param.name,
        description: param.description || '',
        required: param.required || false,
        position: param.in,
      };

      // Set the type based on the schema
      if (param.schema) {
        const schema = param.schema;
        // Set the type based on the schema type
        arg.type = schema.type;

        // Handle enum values
        if (schema.enum && schema.enum.length > 0) {
          arg.enum = schema.enum;
        }

        // Handle array type
        if (schema.type === 'array' && schema.items) {
          arg.items = {
            type: schema.items.type,
          };
        }

        // Handle object type
        if (schema.type === 'object' && schema.properties) {
          arg.properties = {};
          for (const propName in schema.properties) {
            const prop = schema.properties[propName];
            if (prop) {
              arg.properties[propName] = {
                type: prop.type,
              };
              if (prop.description) {
                arg.properties[propName].description = prop.description;
              }
            }
          }
        }
      }

      return arg;
    });
  }

  // Convert request body to arguments
  if (operation.requestBody && operation.requestBody.content) {
    for (const contentType in operation.requestBody.content) {
      const mediaType = operation.requestBody.content[contentType];
      if (mediaType.schema) {
        const schema = mediaType.schema;

        // For JSON and form content types, convert the schema to arguments
        if (
          contentType.includes('application/json') ||
          contentType.includes('application/x-www-form-urlencoded')
        ) {
          // For object type, convert each property to an argument
          if (schema.type === 'object' && schema.properties) {
            for (const propName in schema.properties) {
              const prop = schema.properties[propName];
              if (!prop) {
                continue;
              }

              const arg = {
                name: propName,
                description: prop.description || '',
                type: prop.type,
                required: schema.required && schema.required.includes(propName),
                position: 'body',
              };

              // Handle enum values
              if (prop.enum && prop.enum.length > 0) {
                arg.enum = prop.enum;
              }

              // Handle array type
              if (prop.type === 'array' && prop.items) {
                arg.items = {
                  type: prop.items.type,
                  description: prop.items.description || '',
                };

                if (prop.items.minItems > 0) {
                  arg.items.minItems = prop.items.minItems;
                }

                if (prop.items.type === 'object' && prop.items.properties) {
                  arg.items.properties = prop.items.properties;
                }
              }

              // Handle object type
              if (prop.type === 'object' && prop.properties) {
                arg.properties = {};
                for (const subPropName in prop.properties) {
                  const subProp = prop.properties[subPropName];
                  if (subProp) {
                    const subPropObj = {
                      type: subProp.type,
                      description: subProp.description || '',
                    };

                    if (subProp.default !== undefined) {
                      subPropObj.default = subProp.default;
                    }

                    if (subProp.enum) {
                      subPropObj.enum = subProp.enum;
                    }

                    arg.properties[subPropName] = subPropObj;
                  }
                }
              }

              // Handle allOf
              if (!prop.type && prop.allOf && prop.allOf.length === 1) {
                arg.type = 'object';
                arg.properties = allOfHandle(prop.allOf[0]);
              }

              tool.args.push(arg);
            }
          }
        }
      }
      break; // Only use the first content type
    }
  }

  // Sort arguments by name for consistent output
  tool.args.sort((a, b) => a.name.localeCompare(b.name));

  return tool;
}

function allOfHandle(schema) {
  const properties = {};

  if (schema.type === 'object' && schema.properties) {
    for (const propName in schema.properties) {
      const prop = schema.properties[propName];
      if (prop) {
        properties[propName] = {
          type: prop.type,
        };

        if (prop.description) {
          properties[propName].description = prop.description;
        }

        // Handle nested allOf
        if (!prop.type && prop.allOf && prop.allOf.length === 1) {
          properties[propName].type = 'object';
          properties[propName].properties = allOfHandle(prop.allOf[0]);
        }
      }
    }
  }

  return properties;
}

function createRequestTemplate(path, method, operation, servers) {
  let serverURL = servers && servers.length > 0 ? servers[0].url : '';
  let fullUrl = '';
  try {
    // Prefer the standard URL API for safe joining; it preserves protocol and slashes correctly
    fullUrl = new URL(path, serverURL).toString();
  } catch (e) {
    // Fallback for very old environments or invalid base: keep protocol double slashes, normalize others
    if (typeof serverURL === 'string') {
      serverURL = serverURL.trim();
      const m = serverURL.match(/^(https?:\/\/)(.*)$/i);
      if (m) {
        serverURL = m[1] + m[2].replace(/\/{2,}/g, '/');
      } else {
        serverURL = serverURL.replace(/\/{2,}/g, '/');
      }
      // Trim trailing slashes to avoid double slashes when concatenating with path
      serverURL = serverURL.replace(/\/+$/, '');
    }
    // Ensure path starts with a single slash and collapse duplicates
    const normalizedPath = typeof path === 'string' ? ('/' + path).replace(/\/{2,}/g, '/').replace(/^\/+/, '/') : '';
    fullUrl = serverURL + normalizedPath;
  }

  const template = {
    url: fullUrl,
    method: method.toUpperCase(),
    headers: [],
  };

  // Process operation-level security requirements
  if (operation.security && operation.security.length > 0) {
    for (const securityRequirement of operation.security) {
      for (const schemeName in securityRequirement) {
        template.security = { id: schemeName };
        break;
      }
    }
  }

  // Add Content-Type header based on request body content type
  if (operation.requestBody) {
    for (const contentType in operation.requestBody.content) {
      template.headers.push({
        key: 'Content-Type',
        value: contentType,
      });
      break; // Only use the first content type
    }
  }

  return template;
}

/**
 * Create a response template from an OpenAPI operation
 * @param {object} operation OpenAPI operation object
 * @returns {object} Response template object
 */
function createResponseTemplate(operation) {
  // Find the success response (200, 201, etc.)
  let successResponse = null;
  if (operation.responses) {
    for (const [code, responseRef] of Object.entries(operation.responses)) {
      if (code.startsWith('2') && responseRef) {
        successResponse = responseRef;
        break;
      }
    }
  }

  // If there's no success response, don't add a response template
  if (!successResponse || Object.keys(successResponse.content || {}).length === 0) {
    return {};
  }

  // Create the response template
  const template = {
    prependBody:
      '# API Response Information\n\n' +
      "Below is the response from an API call. To help you understand the data, I've provided:\n\n" +
      '1. A detailed description of all fields in the response structure\n' +
      '2. The complete API response\n\n' +
      '## Response Structure\n\n',
  };

  // Process each content type
  for (const [contentType, mediaType] of Object.entries(successResponse.content)) {
    if (!mediaType.schema) {
      continue;
    }

    template.prependBody += `> Content-Type: ${contentType}\n\n`;
    const schema = mediaType.schema;

    // Generate field descriptions using recursive function
    if (schema.type === 'array' && schema.items) {
      // Handle array type
      template.prependBody += '- **items**: Array of items (Type: array)\n';
      // Process array items recursively
      processSchemaProperties(template, schema.items, 'items', 1, 10);
    } else if (schema.type === 'object' && Object.keys(schema.properties || {}).length > 0) {
      // Get property names and sort them alphabetically for consistent output
      const propNames = Object.keys(schema.properties).sort();

      // Process properties in alphabetical order
      for (const propName of propNames) {
        const propRef = schema.properties[propName];
        if (!propRef) {
          continue;
        }

        // Write the property description
        template.prependBody += `- **${propName}**: ${propRef.description || ''}`;
        if (propRef.type) {
          template.prependBody += ` (Type: ${propRef.type})`;
        }
        template.prependBody += '\n';

        // Process nested properties recursively
        processSchemaProperties(template, propRef, propName, 1, 10);
      }
    }
  }

  template.prependBody += '\n## Original Response\n\n';
  return template;
}

/**
 * Process schema properties recursively
 * @param {object} template Response template object
 * @param {object} schema OpenAPI schema object
 * @param {string} path Current property path
 * @param {number} depth Current recursion depth
 * @param {number} maxDepth Maximum recursion depth
 */
function processSchemaProperties(template, schema, path, depth, maxDepth) {
  if (depth > maxDepth) {
    return; // Stop recursion if max depth is reached
  }

  // Calculate indentation based on depth
  const indent = '  '.repeat(depth);

  // Handle array type
  if (schema.type === 'array' && schema.items) {
    const arrayItemSchema = schema.items;

    // If array items are objects, describe their properties
    if (
      arrayItemSchema.type === 'object' &&
      Object.keys(arrayItemSchema.properties || {}).length > 0
    ) {
      // Get property names for consistent output
      const propNames = Object.keys(arrayItemSchema.properties).sort();

      // Process each property
      for (const propName of propNames) {
        const propRef = arrayItemSchema.properties[propName];
        if (!propRef) {
          continue;
        }

        // Write the property description
        const propPath = `${path}[][${propName}]`;
        template.prependBody += `${indent}- **${propPath}**: ${propRef.description || ''}`;
        if (propRef.type) {
          template.prependBody += ` (Type: ${propRef.type})`;
        }
        template.prependBody += '\n';

        // Process nested properties recursively
        processSchemaProperties(template, propRef, propPath, depth + 1, maxDepth);
      }
    } else if (arrayItemSchema.type) {
      // If array items are not objects, just describe the array item type
      template.prependBody += `${indent}- **${path}[]**: Items of type ${arrayItemSchema.type}\n`;
    }
    return;
  }

  // Handle object type
  if (schema.type === 'object' && Object.keys(schema.properties || {}).length > 0) {
    // Get property names for consistent output
    const propNames = Object.keys(schema.properties).sort();

    // Process each property
    for (const propName of propNames) {
      const propRef = schema.properties[propName];
      if (!propRef) {
        continue;
      }

      // Write the property description
      const propPath = `${path}.${propName}`;
      template.prependBody += `${indent}- **${propPath}**: ${propRef.description || ''}`;
      if (propRef.type) {
        template.prependBody += ` (Type: ${propRef.type})`;
      }
      template.prependBody += '\n';

      // Process nested properties recursively
      processSchemaProperties(template, propRef, propPath, depth + 1, maxDepth);
    }
  }
}

// Example usage
// const fs = require('fs');
// const openAPIDocString = fs.readFileSync('path/to/openapi.json', 'utf8');
// const mcpConfig = extractToolsFromOpenAPI(openAPIDocString);
// fs.writeFileSync('path/to/mcp-server.json', JSON.stringify(mcpConfig, null, 2));
