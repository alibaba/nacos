import client from './client';
import type { ApiResult } from './types';
import type {
  McpListParams,
  McpListResponse,
  McpServerDetailInfo,
  McpCreateData,
  McpUpdateData,
  McpTool,
} from '@/types/mcp';

export const mcpApi = {
  /** List MCP servers with pagination and search */
  listMcpServers: (params: McpListParams): ApiResult<McpListResponse> =>
    client.get('v3/console/ai/mcp/list', { params }) as ApiResult<McpListResponse>,

  /** Get MCP server detail */
  getMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    version?: string;
    namespaceId?: string;
  }): ApiResult<McpServerDetailInfo> =>
    client.get('v3/console/ai/mcp', { params }) as ApiResult<McpServerDetailInfo>,

  /** Create a new MCP server */
  createMcpServer: (data: McpCreateData): ApiResult<string> =>
    client.post('v3/console/ai/mcp', data) as ApiResult<string>,

  /** Update an existing MCP server */
  updateMcpServer: (data: McpUpdateData): ApiResult<string> =>
    client.put('v3/console/ai/mcp', data) as ApiResult<string>,

  /** Delete an MCP server */
  deleteMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    namespaceId?: string;
  }): ApiResult<string> =>
    client.delete('v3/console/ai/mcp', { params }) as ApiResult<string>,

  /** Import tools from an external MCP server endpoint */
  importToolsFromMcp: (params: {
    transportType: string;
    baseUrl: string;
    endpoint?: string;
    authToken?: string;
  }): ApiResult<McpTool[]> =>
    client.get('v3/console/ai/mcp/importToolsFromMcp', { params }) as ApiResult<McpTool[]>,
};
