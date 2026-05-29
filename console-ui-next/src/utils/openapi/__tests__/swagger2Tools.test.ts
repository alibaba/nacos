import { describe, expect, it } from 'vitest';
import { extractToolsFromOpenAPI, transformToolsFromConfig } from '../swagger2Tools';

describe('swagger2Tools', () => {
  it('preserves nested object schemas and argsPosition for complex body arguments', () => {
    const openapi = {
      openapi: '3.0.0',
      paths: {
        '/pets': {
          post: {
            operationId: 'createPet',
            requestBody: {
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    required: ['payload'],
                    properties: {
                      payload: {
                        type: 'object',
                        description: 'Pet payload',
                        required: ['name'],
                        properties: {
                          name: { type: 'string', description: 'Pet name' },
                          tags: {
                            type: 'array',
                            items: {
                              type: 'object',
                              required: ['id'],
                              properties: {
                                id: { type: 'integer' },
                              },
                            },
                          },
                        },
                      },
                    },
                  },
                },
              },
            },
            responses: { 200: { description: 'ok' } },
          },
        },
      },
    };

    const config = extractToolsFromOpenAPI(openapi);
    const result = transformToolsFromConfig(config);
    const payload = result.tools[0].inputSchema.properties.payload;
    const template = result.toolsMeta.createPet.templates['json-go-template'];

    expect(payload).toMatchObject({
      type: 'object',
      description: 'Pet payload',
      required: ['name'],
      properties: {
        name: { type: 'string', description: 'Pet name' },
        tags: {
          type: 'array',
          items: {
            type: 'object',
            required: ['id'],
            properties: {
              id: { type: 'integer' },
            },
          },
        },
      },
    });
    expect(template.requestTemplate.argsToJsonBody).toBe(true);
    expect(template.argsPosition).toEqual({ payload: 'body' });
  });

  it('keeps complex query arguments explicit instead of collapsing to argsToUrlParam', () => {
    const openapi = {
      openapi: '3.0.0',
      paths: {
        '/search': {
          get: {
            operationId: 'searchPets',
            parameters: [
              {
                name: 'filter',
                in: 'query',
                schema: {
                  type: 'object',
                  properties: {
                    name: { type: 'string' },
                  },
                },
              },
            ],
            responses: { 200: { description: 'ok' } },
          },
        },
      },
    };

    const config = extractToolsFromOpenAPI(openapi);
    const result = transformToolsFromConfig(config);
    const template = result.toolsMeta.searchPets.templates['json-go-template'];

    expect(result.tools[0].inputSchema.properties.filter).toMatchObject({
      type: 'object',
      properties: {
        name: { type: 'string' },
      },
    });
    expect(template.requestTemplate.argsToUrlParam).toBeUndefined();
    expect(template.argsPosition).toEqual({ filter: 'query' });
  });
});
