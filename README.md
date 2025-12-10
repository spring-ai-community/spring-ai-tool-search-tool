# Tool Search Tool for Spring AI [![build status](https://github.com/spring-ai-community/spring-ai-tool-search-tool/actions/workflows/ci.yml/badge.svg)](https://github.com/spring-ai-community/spring-ai-tool-search-tool/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.springaicommunitytool-search-tool?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/tool-search-tool)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

> NOTE: this is Spring AI 1.1.x (e.g. Boot3.x and Spring Framework 6.x) compatibible version.

Dynamic tool discovery and selection for Spring AI, enabling LLMs to work efficiently with large tool libraries by discovering tools on-demand instead of loading all definitions upfront.


**The Problem**

As AI agents connect to more services—Slack, GitHub, Jira, MCP servers—tool libraries grow rapidly. A typical multi-server setup can easily have 50+ tools consuming **55,000+ tokens** before any conversation starts. Tool selection accuracy also degrades when models face 30+ similarly-named tools.

<img style="fdisplay: block; margin: 0px auto; padding: 20px;" src="https://github.com/spring-io/spring-io-static/blob/main/blog/tzolov/20251208/spring-ai-tool-search-tool-calling-flow.png?raw=true" width="350" align="left"/>

**The Solution**

This project implements the **[Tool Search Tool](https://www.anthropic.com/engineering/advanced-tool-use)** pattern for Spring AI:
- Model receives only a search tool initially (minimal tokens)
- When capabilities are needed, model searches for relevant tools
- Matching tool definitions are dynamically expanded into context
- Model invokes discovered tools to complete the task

**Result:** Significant token savings while maintaining access to thousands of tools.

## Project Structure

```
spring-ai-tool-search-tool/
├── tool-search-tool/           # Core advisor implementation
├── tool-searchers/             # Pluggable search strategies
│   ├── tool-searcher-vectorstore/   # Semantic search (embeddings)
│   ├── tool-searcher-lucene/        # Keyword search (full-text)
│   └── tool-searcher-regex/         # Pattern matching
├── tool-search-tool-bom/       # Bill of Materials
└── examples/
    ├── tool-search-tool-demo/  # Recommended approach
    └── pre-select-tool-demo/   # Alternative approach
```

## Quick Start

### 1. Add Dependencies

Then add the dependencies:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-search-tool</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Choose a search strategy -->
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-searcher-lucene</artifactId>
    <version>1.0.0</version>
</dependency>
```


For snapshots add the snapshot repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

### 2. Configure the Advisor

```java
@Bean
ToolSearcher toolSearcher() {
    return new LuceneToolSearcher(0.4f);
}

@Bean
CommandLineRunner demo(ChatClient.Builder builder, ToolSearcher toolSearcher) {
    return args -> {
        var advisor = ToolSearchToolCallAdvisor.builder()
            .toolSearcher(toolSearcher)
            .build();

        ChatClient chatClient = builder
            .defaultTools(new MyTools())  // 100s of tools - NOT sent to LLM initially
            .defaultAdvisors(advisor)
            .build();

        String answer = chatClient
            .prompt("What's the weather in Amsterdam?")
            .call()
            .content();
    };
}
```

## Search Strategies

| Strategy | Module | Best For |
|----------|--------|----------|
| **Semantic** | `tool-searcher-vectorstore` | Natural language queries, fuzzy matching |
| **Keyword** | `tool-searcher-lucene` | Exact term matching, known tool names |
| **Regex** | `tool-searcher-regex` | Tool name patterns (`get_*`, `*_api`) |

See [Tool Searchers README](tool-searchers/README.md) for detailed documentation.

## How It Works

1. **Indexing**: Tools are indexed in the `ToolSearcher` (not sent to LLM)
2. **Initial Request**: Only `toolSearchTool` definition is sent to LLM
3. **Discovery**: LLM calls `toolSearchTool(query="weather")` to find relevant tools
4. **Expansion**: Discovered tools are added to next request
5. **Execution**: LLM calls discovered tools to complete the task
6. **Response**: Final answer generated

See [Tool Search Tool README](tool-search-tool/README.md) for detailed documentation.

## Examples

### Tool Search Tool Demo (Recommended)

LLM actively discovers tools on-demand:

```bash
cd examples/tool-search-tool-demo
mvn spring-boot:run
```

See [example README](examples/tool-search-tool-demo/README.md).

### Pre-Select Tool Demo (Alternative)

Pre-selects tools based on conversation context:

```bash
cd examples/pre-select-tool-demo
mvn spring-boot:run
```

See [example README](examples/pre-select-tool-demo/README.md).

## Building

```bash
mvn clean install
```

## Requirements

- Java 17+
- Spring AI 1.1.0-M4+
- Maven 3.6+

## Key Benefits

- **Token efficiency** - Reduced context windows (80-90% savings)
- **Better accuracy** - Models select the right tool when focused on fewer options
- **Portable** - Works with any LLM supported by Spring AI
- **Flexible** - Swap search strategies based on your use case
- **Observable** - Leverages Spring AI's advisor chain for logging and monitoring

## Related Resources

- [Anthropic's Tool Search Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool)
- [Anthropic's Advanced Tool Use Blog](https://www.anthropic.com/engineering/advanced-tool-use)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Recursive Advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)

## License

Apache License 2.0 - See [LICENSE.txt](LICENSE.txt)
