# Tool Searchers for Spring AI

This module provides pluggable search strategy implementations for the Tool Search Tool pattern. Each searcher indexes tool definitions and enables efficient discovery based on different search approaches.

## Available Implementations

| Module | Search Type | Best For |
|--------|-------------|----------|
| [`tool-searcher-vectorstore`](#vectortoolsearcher) | Semantic/Embedding | Natural language queries, fuzzy matching |
| [`tool-searcher-lucene`](#lucenetoolsearcher) | Keyword/Full-text | Exact term matching, known tool names |
| [`tool-searcher-regex`](#regextoolsearcher) | Pattern matching | Tool name patterns (`get_*`, `*_api`) |

## ToolSearcher Interface

All implementations share the common `ToolSearcher` interface:

```java
public interface ToolSearcher {
    
    // Returns the search type (SEMANTIC, KEYWORD, REGEX)
    SearchType searchType();
    
    // Index a tool for later discovery
    void indexTool(String sessionId, ToolReference toolReference);
    
    // Search for tools matching the query
    ToolSearchResponse search(ToolSearchRequest request);
    
    // Clear indexed tools for a session
    void clearIndex(String sessionId);
}
```

---

## VectorToolSearcher

**Module:** `tool-searcher-vectorstore`

Uses Spring AI's `VectorStore` for embedding-based semantic search. Tool descriptions are converted to embeddings and stored, enabling similarity-based retrieval.

### Installation

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-searcher-vectorstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Configuration

```java
@Bean
ToolSearcher vectorToolSearcher(VectorStore vectorStore) {
    return new VectorToolSearcher(vectorStore);
}
```

### How It Works

1. **Indexing**: Tool name + description are embedded and stored in the vector store
2. **Search**: Query is embedded and nearest neighbors are retrieved
3. **Results**: Tools are ranked by cosine similarity score

### Best For

- Natural language queries: *"I need to check the weather"*
- Fuzzy matching: *"send notification"* → finds `notification-send-user`
- Semantic understanding: *"current time"* → finds `getCurrentDateTime`

### Prerequisites

Requires a configured `VectorStore` bean (e.g., `SimpleVectorStore`, `PgVectorStore`, `ChromaVectorStore`).

---

## LuceneToolSearcher

**Module:** `tool-searcher-lucene`

Uses Apache Lucene for keyword-based full-text search. Efficient for exact term matching and prefix queries.

### Installation

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-searcher-lucene</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Configuration

```java
@Bean
ToolSearcher luceneToolSearcher() {
    return new LuceneToolSearcher(0.4f);  // similarity threshold
}
```

### Constructor Parameters

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `similarityThreshold` | `float` | Minimum score for results (0.0 - 1.0) | `0.4f` |

### How It Works

1. **Indexing**: Tool name and description are tokenized and indexed
2. **Search**: Query is parsed and matched against indexed terms
3. **Results**: Tools are ranked by Lucene's TF-IDF scoring

### Best For

- Exact term matching: *"weather"* → finds `weather`, `getWeather`
- Known tool names: *"github"* → finds `github_create_issue`
- Fast lookups with large tool sets

### Features

- In-memory index (no external dependencies)
- Automatic tokenization and stemming
- Session isolation via RAM directories

---

## RegexToolSearcher

**Module:** `tool-searcher-regex`

Uses regular expression pattern matching against tool names and descriptions.

### Installation

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>tool-searcher-regex</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Configuration

```java
@Bean
ToolSearcher regexToolSearcher() {
    return new RegexToolSearcher();
}
```

### How It Works

1. **Indexing**: Tools are stored in a session-scoped map
2. **Search**: Query is compiled as regex and matched against tool name/description
3. **Results**: All matching tools are returned

### Best For

- Tool name patterns: `get_*` → finds all tools starting with `get_`
- Category matching: `*_database_*` → finds database-related tools
- Wildcard queries: `.*weather.*` → finds anything containing "weather"

### Query Examples

| Query | Matches |
|-------|---------|
| `get.*` | `getWeather`, `getCurrentTime`, `getUser` |
| `.*_api` | `slack_api`, `github_api` |
| `notification.*` | `notification_send`, `notification_list` |
| `(?i)weather` | `Weather`, `getWeather`, `WEATHER_API` |

---


## License

Apache License 2.0
