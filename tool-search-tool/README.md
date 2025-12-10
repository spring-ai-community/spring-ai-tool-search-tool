# Tool Search Tool for Spring AI

Dynamic tool discovery for Spring AI that lets LLMs find and use tools on-demand instead of loading all tool definitions upfront.

## Overview

As AI agents connect to more services (Slack, GitHub, Jira, MCP servers), tool libraries grow rapidly. A typical multi-server setup can easily have 50+ tools consuming **55,000+ tokens** before any conversation starts. Tool selection accuracy also degrades when models face 30+ similarly-named tools.

The **Tool Search Tool** pattern solves this by enabling on-demand tool discovery:
- Model receives only a search tool initially (minimal tokens)
- When capabilities are needed, model searches for relevant tools
- Matching tool definitions are dynamically expanded into context
- Model can then invoke the discovered tools

This achieves **significant token savings** while maintaining access to thousands of tools.

## How It Works

The `ToolSearchToolCallAdvisor` extends Spring AI's `ToolCallAdvisor` to implement dynamic tool discovery:

<img style="float: center; display: block; margin: auto;" src="https://github.com/spring-io/spring-io-static/blob/main/blog/tzolov/20251208/spring-ai-tool-search-tool-calling-flow.png?raw=true" width="600"/>

1. **Indexing**: At conversation start, all registered tools are indexed in the `ToolSearcher` (but NOT sent to the LLM)
2. **Initial Request**: Only the Tool Search Tool (TST) definition is sent to the LLM
3. **Discovery Call**: When the LLM needs capabilities, it calls the TST with a search query
4. **Search & Expand**: The `ToolSearcher` finds matching tools and their definitions are added to the next request
5. **Tool Invocation**: The LLM sees both TST and discovered tool definitions, and can call the actual tool
6. **Tool Execution**: The discovered tool is executed and results returned to the LLM
7. **Response**: The LLM generates the final answer using the tool results

## Installation

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-search-tool</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>

<!-- Choose a search strategy -->
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-searcher-vectorstore</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
// 1. Configure a ToolSearcher (e.g., VectorToolSearcher for semantic search)
@Bean
ToolSearcher toolSearcher(VectorStore vectorStore) {
    return new VectorToolSearcher(vectorStore);
}

// 2. Create the advisor
var advisor = ToolSearchToolCallAdvisor.builder()
    .toolSearcher(toolSearcher)
    .maxResults(5)  // Optional: limit search results
    .build();

// 3. Use with ChatClient
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(new MyTools())  // 100s of tools registered but NOT sent to LLM initially
    .defaultAdvisors(advisor)
    .build();

// 4. Make requests - tools are discovered on-demand
String answer = chatClient.prompt("What's the weather in Amsterdam?")
    .call()
    .content();
```

## Search Strategies

The `ToolSearcher` interface abstracts the search implementation. Three implementations are provided:

| Strategy | Module | Best For |
|----------|--------|----------|
| **Semantic** | `tool-searcher-vectorstore` | Natural language queries, fuzzy matching |
| **Keyword** | `tool-searcher-lucene` | Exact term matching, known tool names |
| **Regex** | `tool-searcher-regex` | Tool name patterns (`get_*_data`) |

### VectorToolSearcher (Semantic)

```java
@Bean
ToolSearcher vectorToolSearcher(VectorStore vectorStore) {
    return new VectorToolSearcher(vectorStore);
}
```

Uses embedding-based similarity search. Best for natural language queries where users describe what they need.

### LuceneToolSearcher (Keyword)

```java
@Bean
ToolSearcher luceneToolSearcher() {
    return new LuceneToolSearcher(0.4f);  // similarity threshold
}
```

Uses Apache Lucene for keyword-based search. Fast and effective for exact term matching.

### RegexToolSearcher (Pattern)

```java
@Bean
ToolSearcher regexToolSearcher() {
    return new RegexToolSearcher();
}
```

Uses regex pattern matching. Good for tool name patterns like `get_*` or `*_database_*`.

## Configuration Options

### ToolSearchToolCallAdvisor.Builder

| Option | Description | Default |
|--------|-------------|---------|
| `toolSearcher(ToolSearcher)` | The search implementation to use | Required |
| `maxResults(Integer)` | Maximum tool references to return | `5` |
| `systemMessageSuffix(String)` | Custom prompt suffix for tool discovery instructions | Default template |
| `referenceToolNameAccumulation(boolean)` | Accumulate all discovered tools vs. only latest | `true` |
| `advisorOrder(int)` | Order in the advisor chain | `HIGHEST_PRECEDENCE + 300` |

## API Reference

### ToolSearcher Interface

```java
public interface ToolSearcher {
    SearchType searchType();
    void indexTool(String sessionId, ToolReference toolReference);
    ToolSearchResponse search(ToolSearchRequest request);
    void clearIndex(String sessionId);
}
```

### ToolReference

```java
ToolReference.builder()
    .toolName("weather")
    .summary("Get current weather for a location")
    .relevanceScore(0.95)
    .build();
```

### ToolSearchRequest

```java
new ToolSearchRequest(
    sessionId,      // Session identifier for tool isolation
    query,          // Search query
    maxResults,     // Max results to return
    categoryFilter  // Optional category filter
);
```

## When to Use

**Good fit:**
- 10+ tools in your system
- Tool definitions consuming >10K tokens
- Building MCP-powered systems with multiple servers
- Experiencing tool selection accuracy issues

**Traditional approach may be better:**
- Small tool library (<10 tools)
- All tools frequently used in every session
- Very compact tool definitions

## Related Resources

- [Anthropic's Tool Search Tool Documentation](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool)
- [Anthropic's Advanced Tool Use Blog Post](https://www.anthropic.com/engineering/advanced-tool-use)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Recursive Advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)

## License

Apache License 2.0
