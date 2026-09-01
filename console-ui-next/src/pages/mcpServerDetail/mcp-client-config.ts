import type {
  McpPackage,
  McpPackageArgument,
  McpServerDetailInfo,
} from '@/types/mcp';
import { buildEndpointUrl } from '@/lib/mcp-endpoint-utils';

export interface McpClientConfiguration {
  key: string;
  label: string;
  config: Record<string, unknown>;
}

type CompatiblePackageArgument = McpPackageArgument & {
  default?: string | boolean;
  valueHint?: string;
  value_hint?: string;
  variables?: Record<string, { value?: string; default?: string }>;
};

type CompatibleEnvironmentVariable = NonNullable<McpPackage['environmentVariables']>[number] & {
  variables?: Record<string, { value?: string; default?: string }>;
};

type CompatiblePackage = McpPackage & {
  runtime_hint?: string;
  runtime_arguments?: CompatiblePackageArgument[];
  package_arguments?: CompatiblePackageArgument[];
  environment_variables?: McpPackage['environmentVariables'];
};

const REGISTRY_COMMANDS: Record<string, string> = {
  npm: 'npx',
  pip: 'python',
  docker: 'docker',
  uv: 'uvx',
  dnx: 'dnx',
  oci: 'docker',
};

function wrapServerConfig(name: string, serverConfig: Record<string, unknown>) {
  return {
    mcpServers: {
      [name]: serverConfig,
    },
  };
}

function replaceVariables(
  value: string | boolean,
  variables?: Record<string, { value?: string; default?: string }>,
) {
  if (!variables || typeof value !== 'string') return value;
  return Object.entries(variables).reduce((result, [key, variable]) => (
    result.replaceAll(`{${key}}`, variable.value || variable.default || `<${key}>`)
  ), value);
}

function formatArgument(argument: CompatiblePackageArgument): string[] {
  const value = argument.value;
  if (argument.type === 'positional') {
    if (value) return [String(replaceVariables(value, argument.variables))];
    const valueHint = argument.valueHint || argument.value_hint;
    if (valueHint) return [`<${valueHint}>`];
    if (argument.default) {
      return [String(replaceVariables(argument.default, argument.variables))];
    }
    return [];
  }

  if (argument.type === 'named' && argument.name) {
    const namedValue = value || argument.default;
    if (namedValue === true || namedValue === 'true') return [argument.name];
    if (namedValue) {
      return [`${argument.name}=${String(replaceVariables(namedValue, argument.variables))}`];
    }
    return [`${argument.name}=<value>`];
  }

  return [];
}

function environmentPlaceholder(name: string): string {
  if (name.includes('API_KEY') || name.includes('TOKEN')) return `YOUR_${name}_HERE`;
  if (name.includes('URL')) return 'https://api.example.com';
  if (name.includes('PORT')) return '3000';
  return `<${name}>`;
}

function buildPackageServerConfig(pkg: CompatiblePackage): Record<string, unknown> | null {
  const packageName = pkg.identifier || pkg.name;
  if (!packageName) return null;

  const registryType = (pkg.registryType || '').toLowerCase();
  const command = pkg.runtimeHint || pkg.runtime_hint || REGISTRY_COMMANDS[registryType] || 'npx';
  const runtimeArguments = (
    pkg.runtimeArguments || pkg.runtime_arguments || []
  ) as CompatiblePackageArgument[];
  const packageArguments = (
    pkg.packageArguments || pkg.package_arguments || []
  ) as CompatiblePackageArgument[];
  const args = runtimeArguments.flatMap(formatArgument);
  const packageAlreadyPresent = args.some((argument) => argument.includes(packageName));

  if (!packageAlreadyPresent) {
    if (registryType === 'npm' && command === 'npx') {
      if (!args.includes('-y')) args.push('-y');
      args.push(pkg.version && pkg.version !== 'latest'
        ? `${packageName}@${pkg.version}`
        : packageName);
    } else if (registryType === 'docker' || registryType === 'oci') {
      args.push('run', '--rm', '-i');
      args.push(pkg.version && pkg.version !== 'latest'
        ? `${packageName}:${pkg.version}`
        : packageName);
    } else if (registryType === 'pip' || registryType === 'uv') {
      args.push('-m', packageName.split('/').pop() || packageName);
    } else {
      args.push(packageName);
      if (pkg.version && pkg.version !== 'latest') args.push(pkg.version);
    }
  }
  args.push(...packageArguments.flatMap(formatArgument));

  const result: Record<string, unknown> = { command, args };
  const environmentVariables = (
    pkg.environmentVariables || pkg.environment_variables || []
  ) as CompatibleEnvironmentVariable[];
  const env = Object.fromEntries(environmentVariables
    .filter((item) => item.name)
    .map((item) => [
      item.name,
      replaceVariables(
        item.value || item.default || environmentPlaceholder(item.name),
        item.variables,
      ),
    ]));
  if (Object.keys(env).length > 0) result.env = env;
  return result;
}

function resolveClientEndpoints(server: McpServerDetailInfo) {
  const frontendEndpoints = server.frontendEndpoints || [];
  const backendEndpoints = server.backendEndpoints || [];
  const isRestToMcp = server.protocol === 'http' || server.protocol === 'https';
  if (isRestToMcp) return frontendEndpoints;
  return frontendEndpoints.length > 0 ? frontendEndpoints : backendEndpoints;
}

export function buildMcpClientConfigurations(
  server: McpServerDetailInfo,
): McpClientConfiguration[] {
  if (server.frontProtocol === 'stdio' || server.protocol === 'stdio') {
    if ((server.packages?.length || 0) > 0) {
      return server.packages!.flatMap((pkg, index): McpClientConfiguration[] => {
        const serverConfig = buildPackageServerConfig(pkg as CompatiblePackage);
        if (!serverConfig) return [];
        const packageName = pkg.identifier || pkg.name || String(index + 1);
        return [{
          key: `package-${index}`,
          label: packageName,
          config: wrapServerConfig(server.name, serverConfig),
        }];
      });
    }

    if (!server.localServerConfig) return [];
    const existingServers = server.localServerConfig.mcpServers;
    if (existingServers && typeof existingServers === 'object') {
      return [{ key: 'local', label: server.name, config: server.localServerConfig }];
    }
    return [{
      key: 'local',
      label: server.name,
      config: wrapServerConfig(server.name, server.localServerConfig),
    }];
  }

  return resolveClientEndpoints(server).flatMap((endpoint, index): McpClientConfiguration[] => {
    const url = buildEndpointUrl(endpoint);
    if (!url) return [];
    return [{
      key: `endpoint-${index}`,
      label: url,
      config: wrapServerConfig(server.name, { url }),
    }];
  });
}
