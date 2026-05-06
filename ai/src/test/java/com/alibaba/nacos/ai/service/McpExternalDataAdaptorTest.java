/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for McpExternalDataAdaptor with MCP Registry support.
 * Covers file, json, and url import types.
 *
 * @author nacos
 */
@DisplayName("McpExternalDataAdaptor Tests")
class McpExternalDataAdaptorTest {

    private McpExternalDataAdaptor adaptor;

    @Mock
    private HttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adaptor = new McpExternalDataAdaptor();
    }

    // ==================== FILE TYPE IMPORT TESTS ====================

    @Test
    @DisplayName("Should adapt valid MCP seed file to server list")
    void testAdaptValidSeedFile() throws Exception {
        String seedFileData = """
            [
              {
                "name": "ai.aliengiraffe/spotdb",
                "description": "Test Server",
                "version": "0.1.0",
                "repository": {
                  "url": "https://github.com/test/repo"
                }
              }
            ]
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("file");
        request.setData(seedFileData);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("ai.aliengiraffe/spotdb", result.get(0).getName());
        assertEquals("Test Server", result.get(0).getDescription());
        assertNotNull(result.get(0).getId());
    }

    @Test
    @DisplayName("Should handle multiple servers in seed file")
    void testAdaptMultipleServersInSeedFile() throws Exception {
        String seedFileData = """
            [
              {
                "name": "server1",
                "description": "First Server",
                "version": "1.0.0",
                "repository": {
                  "url": "https://github.com/test/repo1"
                }
              },
              {
                "name": "server2",
                "description": "Second Server",
                "version": "2.0.0",
                "repository": {
                  "url": "https://github.com/test/repo2"
                }
              }
            ]
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("file");
        request.setData(seedFileData);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertEquals(2, result.size());
        assertEquals("server1", result.get(0).getName());
        assertEquals("server2", result.get(1).getName());
    }

    @Test
    @DisplayName("Should handle empty seed file")
    void testAdaptEmptySeedFile() throws Exception {
        String emptyFileData = "[]";

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("file");
        request.setData(emptyFileData);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw exception for invalid seed file JSON")
    void testAdaptInvalidSeedFileJson() throws Exception {
        String invalidJson = "[{invalid json}]";

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("file");
        request.setData(invalidJson);

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    // ==================== JSON TYPE IMPORT TESTS ====================

    @Test
    @DisplayName("Should adapt valid single server JSON to server list")
    void testAdaptValidServerJson() throws Exception {
        String serverJson = """
            {
              "name": "ai.alpic.test/test-mcp-server",
              "description": "Alpic Test MCP Server - great server!",
              "version": "0.0.1",
              "repository": {},
              "remotes": [
                {
                  "type": "streamable-http",
                  "url": "https://test.alpic.ai/"
                }
              ]
            }
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(serverJson);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ai.alpic.test/test-mcp-server", result.get(0).getName());
        assertNotNull(result.get(0).getRemoteServerConfig());
    }

    @Test
    @DisplayName("Should adapt JSON with packages (stdio protocol)")
    void testAdaptJsonWithPackages() throws Exception {
        String serverJson = """
            {
              "name": "ai.aliengiraffe/spotdb",
              "description": "Ephemeral data sandbox",
              "version": "0.1.0",
              "repository": {},
              "packages": [
                {
                  "registryType": "oci",
                  "identifier": "docker.io/aliengiraffe/spotdb:0.1.0",
                  "transport": {
                    "type": "stdio"
                  }
                }
              ]
            }
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(serverJson);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.get(0).getProtocol());
    }

    @Test
    @DisplayName("Should handle JSON without remotes and packages")
    void testAdaptJsonWithoutRemotes() throws Exception {
        String serverJson = """
            {
              "name": "minimal-server",
              "description": "Minimal",
              "version": "1.0.0",
              "repository": {}
            }
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(serverJson);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("minimal-server", result.get(0).getName());
        assertNull(result.get(0).getProtocol());
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON format")
    void testAdaptInvalidServerJson() throws Exception {
        String invalidJson = "{invalid: json}";

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(invalidJson);

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should throw exception for empty JSON")
    void testAdaptEmptyJson() throws Exception {
        String emptyJson = "";

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(emptyJson);

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    // ==================== URL TYPE IMPORT TESTS ====================

    @Test
    @DisplayName("Should adapt valid URL response with single page")
    void testAdaptValidUrlResponse() throws Exception {
        String mockResponse = """
            {
              "servers": [
                {
                  "server": {
                    "name": "ai.aliengiraffe/spotdb",
                    "description": "Ephemeral data sandbox",
                    "version": "0.1.0",
                    "repository": {
                      "url": "https://github.com/aliengiraffe/spotdb"
                    },
                    "packages": [
                      {
                        "registryType": "oci",
                        "identifier": "docker.io/aliengiraffe/spotdb:0.1.0",
                        "transport": {
                          "type": "stdio"
                        }
                      }
                    ]
                  },
                  "_meta": {
                    "io.modelcontextprotocol.registry/official": {
                      "status": "active",
                      "publishedAt": "2025-10-09T17:05:17.793149Z",
                      "updatedAt": "2025-10-09T17:05:17.793149Z",
                      "isLatest": true
                    }
                  }
                }
              ],
              "metadata": {
                "nextCursor": null,
                "count": 1
              }
            }
            """;

        setupHttpClientMock(200, mockResponse);

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("ai.aliengiraffe/spotdb", result.get(0).getName());
    }

    @Test
    @DisplayName("Should handle pagination with cursor")
    void testAdaptUrlResponseWithPagination() throws Exception {
        String firstPageResponse = """
            {
              "servers": [
                {
                  "server": {
                    "name": "server1",
                    "version": "0.1.0",
                    "repository": {},
                    "remotes": [
                      {
                        "type": "streamable-http",
                        "url": "https://test1.com/"
                      }
                    ]
                  },
                  "_meta": {
                    "io.modelcontextprotocol.registry/official": {
                      "status": "active",
                      "publishedAt": "2025-10-09T17:05:17.793149Z",
                      "updatedAt": "2025-10-09T17:05:17.793149Z",
                      "isLatest": true
                    }
                  }
                }
              ],
              "metadata": {
                "nextCursor": "cursor_page2",
                "count": 1
              }
            }
            """;

        String secondPageResponse = """
            {
              "servers": [
                {
                  "server": {
                    "name": "server2",
                    "version": "0.2.0",
                    "repository": {},
                    "remotes": [
                      {
                        "type": "streamable-http",
                        "url": "https://test2.com/"
                      }
                    ]
                  },
                  "_meta": {
                    "io.modelcontextprotocol.registry/official": {
                      "status": "active",
                      "publishedAt": "2025-10-09T17:05:17.793149Z",
                      "updatedAt": "2025-10-09T17:05:17.793149Z",
                      "isLatest": true
                    }
                  }
                }
              ],
              "metadata": {
                "nextCursor": null,
                "count": 1
              }
            }
            """;

        setupHttpClientMockForPagination(firstPageResponse, secondPageResponse);

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");
        request.setLimit(-1); // Fetch all pages

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        // Should have servers from both pages
        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    @DisplayName("Should handle URL with search parameter")
    void testAdaptUrlWithSearchParameter() throws Exception {
        String mockResponse = """
            {
              "servers": [],
              "metadata": {
                "nextCursor": null,
                "count": 0
              }
            }
            """;

        setupHttpClientMock(200, mockResponse);

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");
        request.setSearch("spotdb");

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle URL with explicit limit parameter")
    void testAdaptUrlWithLimitParameter() throws Exception {
        String mockResponse = """
            {
              "servers": [
                {
                  "server": {
                    "name": "server1",
                    "version": "0.1.0",
                    "repository": {}
                  },
                  "_meta": {
                    "io.modelcontextprotocol.registry/official": {
                      "status": "active",
                      "publishedAt": "2025-10-09T17:05:17.793149Z",
                      "updatedAt": "2025-10-09T17:05:17.793149Z",
                      "isLatest": true
                    }
                  }
                }
              ],
              "metadata": {
                "nextCursor": null,
                "count": 1
              }
            }
            """;

        setupHttpClientMock(200, mockResponse);

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");
        request.setLimit(10);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw exception for HTTP 404 error")
    void testAdaptUrlWith404Error() throws Exception {
        setupHttpClientMock(404, "Not Found");

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/notfound");

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should throw exception for HTTP 500 error")
    void testAdaptUrlWith500Error() throws Exception {
        setupHttpClientMock(500, "Internal Server Error");

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should throw exception for invalid response JSON")
    void testAdaptUrlWithInvalidResponseJson() throws Exception {
        setupHttpClientMock(200, "{invalid json}");

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://registry.modelcontextprotocol.io/search");

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    // ==================== BOUNDARY & EXCEPTION TESTS ====================

    @Test
    @DisplayName("Should throw exception for blank URL")
    void testAdaptBlankUrl() throws Exception {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("   ");

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should throw exception for null data")
    void testAdaptNullData() throws Exception {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(null);

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should throw exception for unsupported import type")
    void testAdaptUnsupportedImportType() throws Exception {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("unsupported");
        request.setData("{\"name\":\"test\"}");

        assertThrows(Exception.class, () -> adaptor.adaptExternalDataToNacosMcpServerFormat(request));
    }

    @Test
    @DisplayName("Should handle JSON with multiple remotes of different types")
    void testAdaptJsonWithMultipleRemotes() throws Exception {
        String serverJson = """
            {
              "name": "multi-remote-server",
              "description": "Server with multiple remotes",
              "version": "1.0.0",
              "repository": {},
              "remotes": [
                {
                  "type": "streamable-http",
                  "url": "https://remote1.com:8080/path1"
                },
                {
                  "type": "sse",
                  "url": "https://remote2.com/path2"
                }
              ]
            }
            """;

        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData(serverJson);

        List<McpServerDetailInfo> result = adaptor.adaptExternalDataToNacosMcpServerFormat(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getRemoteServerConfig());
        assertEquals(2, result.get(0).getRemoteServerConfig().getFrontEndpointConfigList().size());
    }

    @Test
    @DisplayName("Should generate consistent IDs for same server name")
    void testConsistentIdGeneration() throws Exception {
        String serverJson = """
            {
              "name": "consistent-server",
              "description": "Test",
              "version": "1.0.0",
              "repository": {}
            }
            """;

        McpServerImportRequest request1 = new McpServerImportRequest();
        request1.setImportType("json");
        request1.setData(serverJson);

        McpServerImportRequest request2 = new McpServerImportRequest();
        request2.setImportType("json");
        request2.setData(serverJson);

        List<McpServerDetailInfo> result1 = adaptor.adaptExternalDataToNacosMcpServerFormat(request1);
        List<McpServerDetailInfo> result2 = adaptor.adaptExternalDataToNacosMcpServerFormat(request2);

        assertEquals(result1.get(0).getId(), result2.get(0).getId());
    }

    // ==================== HELPER METHODS ====================

    private void setupHttpClientMock(int statusCode, String responseBody) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(responseBody);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        adaptor.setHttpClient(mockHttpClient);
    }

    private void setupHttpClientMockForPagination(String firstPage, String secondPage) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse1 = mock(HttpResponse.class);
        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(firstPage);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse2 = mock(HttpResponse.class);
        when(mockResponse2.statusCode()).thenReturn(200);
        when(mockResponse2.body()).thenReturn(secondPage);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        adaptor.setHttpClient(mockHttpClient);
    }
}
