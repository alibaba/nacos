declare module 'swagger2openapi' {
  interface ConvertOptions {
    patch?: boolean;
    warnOnly?: boolean;
    [key: string]: unknown;
  }
  interface ConvertResult {
    openapi: unknown;
    [key: string]: unknown;
  }
  export function convertObj(
    swagger: unknown,
    options: ConvertOptions
  ): Promise<ConvertResult>;
}
