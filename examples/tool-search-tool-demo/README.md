# Tool Search Tool Demo

A Spring Boot demonstration project showcasing the **Tool Search Tool** pattern for Spring AI - enabling dynamic, on-demand tool discovery instead of loading all tool definitions upfront.

## Overview

This demo shows how the `ToolSearchToolCallAdvisor` allows an LLM to discover and use tools on-demand, significantly reducing token usage when working with large tool libraries.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- API key for your preferred LLM (configured in `application.properties`)

## Running the Demo

```bash
cd examples/tool-search-tool-demo
mvn spring-boot:run
```

## Key Components

### 1. Main Application (`Application.java`)

Demonstrates the Tool Search Tool pattern:

```java
var toolSearchToolCallAdvisor = ToolSearchToolCallAdvisor.builder()
    .toolSearcher(toolSearcher)
    .referenceToolNameAccumulation(false)  // Don't accumulate discovered tools
    // .maxResults(2)
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultTools(new MyTools(), new DummyTools())  // Tools registered but NOT sent to LLM initially
    .defaultAdvisors(toolSearchToolCallAdvisor)
    .defaultAdvisors(new MyLoggingAdvisor())
    .build();

var answer = chatClient.prompt("""
    Help me plan what to wear today in Landsmeer, NL.
    Please suggest clothing shops that are open right now in the area.

    Do not make assumptions about the date, time. Use the tools for getting the current time.
    """).advisors(new TokenCounterAdvisor()).call().content();
```

**Key aspects:**
- `ToolSearchToolCallAdvisor`: Intercepts tool calling to enable on-demand discovery
- `referenceToolNameAccumulation(false)`: Controls whether discovered tools accumulate across iterations
- `TokenCounterAdvisor`: Tracks token usage for monitoring purposes
- Tools are indexed but NOT sent to the LLM initially - only the search tool is provided
- LLM discovers tools by calling `toolSearchTool` as needed

### 2. Sample Tools (`MyTools` class)

The core tools that will be discovered on-demand by the LLM:

```java
@Tool(description = "Get the weather for a given location and at a given time")
public String weather(String location, @ToolParam(description = "YYYY-MM-DDTHH:mm:ss") String atTime) {
    return "The current weather in " + location + " is sunny with a temperature of 25°C.";
}

@Tool(description = "Get of clothing shops names for a given location and at a given time")
public List<String> clothing(String location, @ToolParam(description = "YYYY-MM-DDTHH:mm:ss") String openAtTime) {
    return List.of("Foo", "Bar", "Baz");
}

@Tool(description = "Provides the current date and time (as date-time string) for a given location")
public String currentTime(String location) {
    return LocalDateTime.now().toString();
}
```

### 3. Dummy Tools (`DummyTools` class)

A collection of **25+ noise tools** used to demonstrate the Tool Search Tool's ability to filter through a large tool library. These tools simulate a realistic scenario where many tools are registered but only a few are relevant to any given request.

They are deliberately **not relevant** to the weather/clothing task, demonstrating how the tool search efficiently finds only the needed tools (`weather`, `clothing`, `currentTime`) among many unrelated options.

### 4. Configuration (`Config.java`)

Configures the `ToolSearcher` implementation. Options include:
- `LuceneToolSearcher` - Keyword-based full-text search (default, with 0.4f threshold)
- `VectorToolSearcher` - Semantic search using embeddings
- `RegexToolSearcher` - Pattern matching

```java
@Bean
ToolSearcher luceneToolSearcher() {
    return new LuceneToolSearcher(0.4f);
}
```

### 5. Logging Advisor (`MyLoggingAdvisor`)

Logs each iteration to show the progressive tool discovery, displaying available tools and message exchanges:

```java
@Override
public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
    Object tools = "No Tools";
    if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
        tools = toolOptions.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();
    }
    // ... format and print USER request with available TOOLS
    return chatClientRequest;
}

@Override
public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
    // ... format and print ASSISTANT response
    return chatClientResponse;
}
```

### 6. Token Counter Advisor (`TokenCounterAdvisor`)

Tracks and reports token usage across all LLM calls, helping to measure the efficiency of the tool search approach:

```java
@Override
public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
    var usage = chatClientResponse.chatResponse().getMetadata().getUsage();
    
    totalTokenCounter.addAndGet(usage.getTotalTokens());
    promptTokenCounter.addAndGet(usage.getPromptTokens());
    completionTokenCounter.addAndGet(usage.getCompletionTokens());

    System.out.println("Current TOKENS Total: " + usage.getTotalTokens() + 
        ", Completion: " + usage.getCompletionTokens() + ", Prompt: " + usage.getPromptTokens());
    System.out.println("Accumulated TOKENS Total: " + totalTokenCounter.get() + 
        ", Completion: " + completionTokenCounter.get() + ", Prompt: " + promptTokenCounter.get());
    
    return chatClientResponse;
}
```

## Expected Flow

When running the demo, you'll see the tool discovery process:

1. **First Request** - LLM receives only `toolSearchTool`
   - LLM calls `toolSearchTool(query="current time")` → discovers `currentTime`

2. **Second Request** - LLM sees `toolSearchTool` + `currentTime`
   - LLM calls `currentTime("Landsmeer, NL")` → returns current time
   - LLM calls `toolSearchTool(query="weather")` → discovers `weather`

3. **Third Request** - LLM sees `toolSearchTool` + `currentTime` + `weather`
   - LLM calls `weather("Landsmeer, NL", "...")` → returns weather
   - LLM calls `toolSearchTool(query="clothing shops")` → discovers `clothing`

4. **Fourth Request** - LLM sees all discovered tools
   - LLM calls `clothing("Landsmeer, NL", "...")` → returns shop list

5. **Final Response** - LLM generates clothing recommendations

## Sample Output

```
USER:
 - SYSTEM 
 - {"messageType":"USER","media":[],"metadata":{},"content":"Help me plan what to wear today..."}
   TOOLS: ["toolSearchTool"]

ASSISTANT:
 - {"messageType":"ASSISTANT","toolCalls":[{"id":"...","type":"FUNCTION","name":"toolSearchTool",...}],...}

Current TOKENS Total: 1234, Completion: 100, Prompt: 1134
Accumulated TOKENS Total: 1234, Completion: 100, Prompt: 1134

USER:
 - SYSTEM 
 - {"messageType":"USER",...}
   TOOLS: ["toolSearchTool","currentTime","weather"]

...

FINAL:
"Based on the sunny 25°C weather in Landsmeer, NL, I recommend light layers.
Here are clothing shops open now: Foo, Bar, Baz..."
```

## Related Documentation

- [Tool Search Tool README](../../tool-search-tool/README.md)
- [Tool Searchers README](../../tool-searchers/README.md)
- [Spring AI Recursive Advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)
