# Nacos example module

This module contains some examples for nacos.

1. Run the Nacos service in standalone mode locally. By default, it uses port 8848.
2. App.java file is a simple "Hello Nacos" program. 
3. ConfigExample.java file demonstrates how to utilize the configuration center in Nacos.
4. NamingExample.java file demonstrates how to use Nacos for service register, deregister, and subscribe.

## Parallel Search MCP

`ParallelSearchMcpExample` registers a remote MCP server in Nacos, discovers its
endpoint, and uses the discovered address to list tools, search the web, and fetch
a public page. It uses the existing Nacos MCP registry and the Java MCP SDK's
Streamable HTTP transport.

[Parallel Search MCP](https://docs.parallel.ai/integrations/mcp/search-mcp) provides
free web search powered by Fast mode and page excerpts without a Parallel account
or API key. Free access is rate limited.

### Run the example

Use JDK 17 or newer and Maven. Start a standalone Nacos server built from the same
`develop` checkout (3.3.0 or newer), with its HTTP and gRPC ports reachable. The
example defaults to `localhost:8848` and the `public` namespace. For an
authenticated Nacos server, set `NACOS_USERNAME` and `NACOS_PASSWORD` to an account
allowed to publish and read MCP servers. Set `NACOS_NAMESPACE` to use another
existing namespace. These credentials are used only for Nacos, not Parallel.

From the repository root:

```bash
mvn -pl example -am install -DskipTests
mvn -pl example dependency:build-classpath -Dmdep.outputFile=target/parallel-mcp-classpath.txt
java -cp "example/target/classes:$(cat example/target/parallel-mcp-classpath.txt)" \
  com.alibaba.nacos.example.ParallelSearchMcpExample
```

The optional arguments are the Nacos server address and a registration name:

```bash
java -cp "example/target/classes:$(cat example/target/parallel-mcp-classpath.txt)" \
  com.alibaba.nacos.example.ParallelSearchMcpExample localhost:8848 my-parallel-example
```

On Windows, use `;` as the classpath separator and read the classpath file using
your shell's equivalent of `cat`.

The output includes:

```text
Registered parallel-search-example version 1.0.0
Discovered endpoint: https://search.parallel.ai:443/mcp
Discovered tools: web_search, web_fetch
web_search:
... search results with source URLs and excerpts ...
web_fetch:
... page excerpts, warnings, and any per-URL errors ...
```

`1.0.0` versions this example's Nacos registration, not the hosted Parallel
service. Registration persists after the program exits. Use a fresh registration
name when re-running, or remove the example's MCP entry through the Nacos console
first. An existing name/version can be rejected as a conflict; the example does
not overwrite it.

The program makes external requests only when explicitly run. It sends the
sample search query, objectives, requested public URL, and a random per-run
`session_id` to `https://search.parallel.ai/mcp`. Search and fetch share that
session ID. Requests carry `User-Agent: alibaba/nacos/<Nacos version>` so Parallel
can measure aggregate usage from this project; the header contains no user or
installation identifier. Nacos authentication is not forwarded to the MCP
server. Existing Nacos defaults and other examples are unchanged.

Each MCP request has a 60-second timeout. Connection, discovery, and tool errors
fail the run instead of silently switching providers. If the free service is rate
limited, wait before running the example again. Search and page content vary over
time, so check for useful URLs and excerpts rather than exact text.
