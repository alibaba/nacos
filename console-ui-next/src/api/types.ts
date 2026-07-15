/**
 * Standard Nacos API response wrapper.
 *
 * Most V3 endpoints return JSON in the shape:
 *   { code: 0, message: "success", data: T }
 *
 * The Axios response interceptor in client.ts already strips the
 * AxiosResponse envelope (i.e. `return response.data`), so every
 * `client.get / post / put / delete` call resolves directly to the
 * HTTP body — which is this ApiResponse<T> object.
 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * Convenience alias for API methods that return the standard wrapper.
 *
 * Usage:
 *   getUser(id: string): ApiResult<User> => client.get(...) as ApiResult<User>
 *
 * The resolved value is `{ code, message, data }`, NOT an AxiosResponse.
 */
export type ApiResult<T> = Promise<ApiResponse<T>>;
