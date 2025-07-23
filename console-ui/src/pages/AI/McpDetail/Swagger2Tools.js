/**
 * Extract tools from OpenAPI document
 * @returns {object} MCP config object
 * @param openapi
 */
export function extractToolsFromOpenAPI(openapi) {
  const mcpConfig = {
    tools: [],
  };

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
  if (operation.parameters) {
    tool.args = operation.parameters.map(param => ({
      name: param.name,
      description: param.description || '',
      type: param.schema ? param.schema.type : '',
      required: param.required || false,
      position: param.in,
    }));
  }

  // Convert request body to arguments
  if (operation.requestBody) {
    for (const contentType in operation.requestBody.content) {
      if (operation.requestBody.content[contentType].schema) {
        const schema = operation.requestBody.content[contentType].schema;
        if (schema.type === 'object' && schema.properties) {
          for (const propName in schema.properties) {
            const prop = schema.properties[propName];
            tool.args.push({
              name: propName,
              description: prop.description || '',
              type: prop.type,
              required: schema.required && schema.required.includes(propName),
              position: 'body',
            });
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

function createRequestTemplate(path, method, operation, servers) {
  let serverURL = servers && servers.length > 0 ? servers[0].url : '';
  serverURL = serverURL.replace(/\/\//g, '/');

  const template = {
    url: serverURL + path,
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
    if (!mediaType.schema || !mediaType.schema.value) {
      continue;
    }

    template.prependBody += `> Content-Type: ${contentType}\n\n`;
    const schema = mediaType.schema.value;

    // Generate field descriptions using recursive function
    if (schema.type === 'array' && schema.items && schema.items.value) {
      // Handle array type
      template.prependBody += '- **items**: Array of items (Type: array)\n';
      // Process array items recursively
      processSchemaProperties(template, schema.items.value, 'items', 1, 10);
    } else if (schema.type === 'object' && Object.keys(schema.properties || {}).length > 0) {
      // Get property names and sort them alphabetically for consistent output
      const propNames = Object.keys(schema.properties).sort();

      // Process properties in alphabetical order
      for (const propName of propNames) {
        const propRef = schema.properties[propName];
        if (!propRef.value) {
          continue;
        }

        // Write the property description
        template.prependBody += `- **${propName}**: ${propRef.value.description || ''}`;
        if (propRef.value.type) {
          template.prependBody += ` (Type: ${propRef.value.type})`;
        }
        template.prependBody += '\n';

        // Process nested properties recursively
        processSchemaProperties(template, propRef.value, propName, 1, 10);
      }
    }
  }
  console.log(template);

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
  if (schema.type === 'array' && schema.items && schema.items.value) {
    const arrayItemSchema = schema.items.value;

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
        if (!propRef.value) {
          continue;
        }

        // Write the property description
        const propPath = `${path}[][${propName}]`;
        template.prependBody += `${indent}- **${propPath}**: ${propRef.value.description || ''}`;
        if (propRef.value.type) {
          template.prependBody += ` (Type: ${propRef.value.type})`;
        }
        template.prependBody += '\n';

        // Process nested properties recursively
        processSchemaProperties(template, propRef.value, propPath, depth + 1, maxDepth);
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
      if (!propRef.value) {
        continue;
      }

      // Write the property description
      const propPath = `${path}.${propName}`;
      template.prependBody += `${indent}- **${propPath}**: ${propRef.value.description || ''}`;
      if (propRef.value.type) {
        template.prependBody += ` (Type: ${propRef.value.type})`;
      }
      template.prependBody += '\n';

      // Process nested properties recursively
      processSchemaProperties(template, propRef.value, propPath, depth + 1, maxDepth);
    }
  }
}

// Example usage
// const fs = require('fs');
// const openAPIDocString = fs.readFileSync('path/to/openapi.json', 'utf8');
// const mcpConfig = extractToolsFromOpenAPI(openAPIDocString);
// fs.writeFileSync('path/to/mcp-server.json', JSON.stringify(mcpConfig, null, 2));
