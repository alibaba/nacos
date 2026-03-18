import client from './client';
import type { AxiosPromise } from 'axios';
import type {
  McpListParams,
  McpListResponse,
  McpServerDetailInfo,
  McpCreateData,
  McpUpdateData,
  McpImportData,
  McpImportValidationResult,
  McpImportResponse,
  McpTool,
} from '@/types/mcp';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export const mcpApi = {
  /** List MCP servers with pagination and search */
  listMcpServers: (params: McpListParams): AxiosPromise<ApiResponse<McpListResponse>> =>
    client.get('v3/console/ai/mcp/list', { params }),

  /** Get MCP server detail */
  getMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    version?: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<McpServerDetailInfo>> =>
    client.get('v3/console/ai/mcp', { params }),

  /** Create a new MCP server */
  createMcpServer: (data: McpCreateData): AxiosPromise<ApiResponse<string>> =>
    client.post('v3/console/ai/mcp', data),

  /** Update an existing MCP server */
  updateMcpServer: (data: McpUpdateData): AxiosPromise<ApiResponse<string>> =>
    client.put('v3/console/ai/mcp', data),

  /** Delete an MCP server */
  deleteMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.delete('v3/console/ai/mcp', { params }),

  /** Import tools from an external MCP server endpoint */
  importToolsFromMcp: (params: {
    transportType: string;
    baseUrl: string;
    endpoint?: string;
    authToken?: string;
  }): AxiosPromise<ApiResponse<McpTool[]>> =>
    client.get('v3/console/ai/mcp/importToolsFromMcp', { params }),

  /** Validate MCP import request */
  validateImport: (data: McpImportData): AxiosPromise<ApiResponse<McpImportValidationResult>> =>
    client.post('v3/console/ai/mcp/import/validate', data),

  /** Execute MCP import */
  executeImport: (data: McpImportData): AxiosPromise<ApiResponse<McpImportResponse>> =>
    client.post('v3/console/ai/mcp/import/execute', data),
};
