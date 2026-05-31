// ===== MCP Server Types =====

export type McpProtocol = 'stdio' | 'mcp-sse' | 'mcp-streamable' | 'http' | 'dubbo' | 'off';
export type McpStatus = 'ACTIVE' | 'DEPRECATED' | 'DELETED';
export type McpSearchMode = 'accurate' | 'blur';
export type McpEndpointType = 'REF' | 'DIRECT';

// ===== Version =====

export interface McpVersionDetail {
  version: string;
  is_latest?: boolean;
}

// ===== Capability =====
// Backend returns capabilities as enum string array: ["TOOL", "PROMPT", "RESOURCE"]
export type McpCapability = string;

// ===== Remote Server Config =====

export interface McpServiceRef {
  serviceName: string;
  groupName: string;
  namespaceId: string;
  transportProtocol?: string;
}

export interface McpRemoteServerConfig {
  serviceRef?: McpServiceRef;
  exportPath?: string;
  frontEndpointConfigList?: McpEndpointInfo[];
}

// ===== Endpoint =====

export interface McpEndpointHeader {
  name: string;
  value?: string;
  default?: string;
  format?: string;
  isRequired?: boolean;
  isSecret?: boolean;
  description?: string;
  choices?: string[];
}

export interface McpEndpointInfo {
  protocol: string;
  address: string;
  port: string;
  path?: string;
  headers?: McpEndpointHeader[];
}

// ===== Tool =====

export interface McpToolAnnotations {
  title?: string;
  readOnlyHint?: boolean;
  destructiveHint?: boolean;
  idempotentHint?: boolean;
  openWorldHint?: boolean;
}

export interface McpTool {
  name: string;
  description?: string;
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  _meta?: Record<string, unknown>;
  annotations?: McpToolAnnotations;
}

export interface McpToolMeta {
  enabled?: boolean;
  invokeContext?: Record<string, string>;
  templates?: {
    'json-go-template'?: {
      requestTemplate?: Record<string, unknown>;
      responseTemplate?: Record<string, unknown>;
      argsPosition?: Record<string, string>;
    };
  };
  transparentAuth?: boolean;
  securitySchemeId?: string;
  clientSecuritySchemeId?: string;
  [key: string]: unknown;
}

// ===== Security =====

export interface McpSecurityScheme {
  id: string;
  type: string;
  scheme?: string;
  in?: string;
  name?: string;
  defaultCredential?: string;
}

export interface McpToolSpecification {
  specificationType?: string;
  tools?: McpTool[];
  toolsMeta?: Record<string, McpToolMeta>;
  securitySchemes?: McpSecurityScheme[];
  extensions?: Record<string, unknown>;
}

// ===== Package (stdio protocol) =====

export interface McpPackageArgument {
  type: 'positional' | 'named';
  value: string;
  name?: string;
  description?: string;
}

export interface McpEnvironmentVariable {
  name: string;
  value?: string;
  default?: string;
  isRequired?: boolean;
  isSecret?: boolean;
  description?: string;
}

export interface McpPackage {
  identifier?: string;
  name?: string;
  version?: string;
  registryType?: string;
  runtimeHint?: string;
  runtimeArguments?: McpPackageArgument[];
  packageArguments?: McpPackageArgument[];
  environmentVariables?: McpEnvironmentVariable[];
}

// ===== Repository & Icon =====

export interface McpRepository {
  url?: string;
  source?: string;
}

export interface McpIcon {
  url: string;
  type?: string;
}

// ===== Server Basic Info =====

export interface McpServerBasicInfo {
  id?: string;
  name: string;
  namespaceId?: string;
  protocol: string;
  frontProtocol: McpProtocol;
  description?: string;
  repository?: McpRepository;
  packages?: McpPackage[];
  icons?: McpIcon[];
  websiteUrl?: string;
  versionDetail?: McpVersionDetail;
  version?: string;
  remoteServerConfig?: McpRemoteServerConfig;
  localServerConfig?: Record<string, unknown>;
  enabled: boolean;
  status?: McpStatus;
  capabilities?: McpCapability[];
}

// ===== Server Detail Info =====

export interface McpServerDetailInfo extends McpServerBasicInfo {
  backendEndpoints?: McpEndpointInfo[];
  frontendEndpoints?: McpEndpointInfo[];
  toolSpec?: McpToolSpecification;
  allVersions?: McpVersionDetail[];
}

// ===== Endpoint Specification =====

export interface McpEndpointSpec {
  type: McpEndpointType;
  data: Record<string, unknown>;
}

// ===== List Request/Response =====

export interface McpListParams {
  mcpName?: string;
  version?: string;
  namespaceId?: string;
  search?: McpSearchMode;
  pageNo?: number;
  pageSize?: number;
}

export interface McpListResponse {
  totalCount: number;
  pageItems: McpServerBasicInfo[];
}

// ===== Create/Update =====

export interface McpCreateData {
  mcpName?: string;
  namespaceId?: string;
  serverSpecification: string;
  toolSpecification?: string;
  endpointSpecification?: string;
}

export interface McpUpdateData extends McpCreateData {
  latest?: boolean;
  overrideExisting?: boolean;
}
