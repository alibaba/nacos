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

  it('converts nullable response fields into JSON Schema union types', () => {
    const openapi = {
      openapi: '3.0.3',
      paths: {
        '/pets/{id}': {
          get: {
            operationId: 'getPet',
            responses: {
              200: {
                description: 'ok',
                content: {
                  'application/json': {
                    schema: {
                      type: 'object',
                      required: ['id'],
                      properties: {
                        id: { type: 'integer' },
                        nickname: {
                          type: 'string',
                          nullable: true,
                          description: 'Optional nickname',
                        },
                        score: { type: 'number', nullable: false },
                        unspecified: { nullable: true },
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    };

    const result = transformToolsFromConfig(extractToolsFromOpenAPI(openapi));

    expect(result.tools[0].outputSchema).toEqual({
      type: 'object',
      required: ['id'],
      properties: {
        id: { type: 'integer' },
        nickname: {
          type: ['string', 'null'],
          description: 'Optional nickname',
        },
        score: { type: 'number' },
        unspecified: { type: 'string' },
      },
    });
  });

  it('converts nullable response fields recursively in objects, arrays, and alternatives', () => {
    const openapi = {
      openapi: '3.0.3',
      paths: {
        '/profiles': {
          get: {
            operationId: 'listProfiles',
            responses: {
              200: {
                description: 'ok',
                content: {
                  'application/json': {
                    schema: {
                      type: 'object',
                      properties: {
                        profile: {
                          type: 'object',
                          nullable: true,
                          properties: {
                            aliases: {
                              type: 'array',
                              items: { type: 'string', nullable: true },
                            },
                            contact: {
                              oneOf: [
                                { type: 'string', nullable: true },
                                { type: 'object', properties: { id: { type: 'integer' } } },
                              ],
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
        },
      },
    };

    const result = transformToolsFromConfig(extractToolsFromOpenAPI(openapi));
    const profile = result.tools[0].outputSchema?.properties.profile;

    expect(profile.type).toEqual(['object', 'null']);
    expect(profile.properties.aliases.items.type).toEqual(['string', 'null']);
    expect(profile.properties.contact.oneOf).toEqual([
      { type: ['string', 'null'] },
      { type: 'object', properties: { id: { type: 'integer' } } },
    ]);
  });

  it('does not add an output schema when a successful response has no schema', () => {
    const openapi = {
      openapi: '3.0.3',
      paths: {
        '/health': {
          get: {
            operationId: 'health',
            responses: { 204: { description: 'no content' } },
          },
        },
      },
    };

    const result = transformToolsFromConfig(extractToolsFromOpenAPI(openapi));

    expect(result.tools[0]).not.toHaveProperty('outputSchema');
  });
});
