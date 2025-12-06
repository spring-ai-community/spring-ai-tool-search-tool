# Tool Search Tool - Generic Design for Multi-LLM Support

This document provides a generic system message and tool description for implementing the Tool Search Tool approach across various LLMs (OpenAI, Anthropic, Gemini, Mistral, etc.).

## Overview

The Tool Search Tool pattern addresses two critical challenges when working with large tool libraries:

1. **Context efficiency**: Tool definitions can consume massive portions of the context window (50 tools ≈ 10-20K tokens)
2. **Tool selection accuracy**: Model's ability to correctly select tools degrades with 30+ tools

Instead of loading all tool definitions upfront, the model uses a search tool to discover relevant tools on-demand.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Request                              │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      LLM Context Window                          │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ System Prompt + tool_search_tool (only ~500 tokens)         │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼ (LLM calls tool_search_tool)
┌─────────────────────────────────────────────────────────────────┐
│                    Tool Search Service                           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Search Index (names, descriptions, parameters)            │   │
│  │ - Vector/Embedding Search                                 │   │
│  │ - BM25/Keyword Search                                     │   │
│  │ - Regex Pattern Search                                    │   │
│  └──────────────────────────────────────────────────────────┘   │
│                         │                                        │
│                         ▼                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Tool Registry (1000s of tools with full definitions)      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼ (Returns tool references)
┌─────────────────────────────────────────────────────────────────┐
│                 Expand Tool References                           │
│  tool_reference → Full tool definition injected into context     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   LLM Invokes Discovered Tool                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Generic System Message

```
You are an AI assistant with access to a large library of tools. However, not all tools are loaded into your context initially to conserve context window space and improve accuracy.

### Tool Discovery Process

You have access to a special tool called `tool_search_tool` that allows you to discover relevant tools on-demand. When you need to perform an action but don't see an appropriate tool in your current context, use `tool_search_tool` to search for it.

**How tool search works:**
1. When you receive a request that may require tools not currently visible to you, call `tool_search_tool` with a search query describing what capability you need
2. The search will return references to matching tools (typically 3-5 most relevant)
3. These tool references will be automatically expanded into full tool definitions that you can then use
4. Select the most appropriate discovered tool and invoke it with the required parameters

**When to use tool search:**
- When you need a capability not provided by your currently available tools
- When the user's request implies operations (database queries, API calls, file operations, etc.) that require specific tools
- When you're unsure which tool to use for a complex task - search can help you discover the right one

**Search query best practices:**
- Use descriptive keywords related to the functionality you need
- Include domain-specific terms (e.g., "github pull request", "slack message", "database query")
- You can search by functionality ("send notification"), by service ("jira"), or by action type ("create ticket")

**Available tool categories in this system:**
[PLACEHOLDER: List your tool categories here, e.g., "Slack messaging, GitHub operations, database queries, file management, calendar scheduling"]

Remember: After discovering tools via search, you must still invoke them using the standard tool calling mechanism. The search only makes tools visible to you - it doesn't execute them.
```

---

## Tool Search Tool Definition

### OpenAI Function Calling Format

```json
{
  "type": "function",
  "function": {
    "name": "tool_search_tool",
    "description": "Search for tools in the tool registry to discover capabilities for completing the current task. Use this when you need functionality not provided by your currently available tools. The search queries against tool names, descriptions, and parameter information to find the most relevant tools. Returns references to matching tools which will be expanded into full definitions you can then invoke.",
    "parameters": {
      "type": "object",
      "properties": {
        "query": {
          "type": "string",
          "description": "A natural language search query describing the tool capability you need. Be specific and include relevant keywords. Examples: 'send slack message to channel', 'create github pull request', 'query database for user records', 'schedule calendar meeting'"
        },
        "search_type": {
          "type": "string",
          "enum": ["semantic", "keyword", "regex"],
          "default": "semantic",
          "description": "The type of search to perform. 'semantic' uses embedding-based similarity (best for natural language queries), 'keyword' uses BM25/TF-IDF (best for exact term matching), 'regex' uses pattern matching (best for tool name patterns like 'get_*_data')"
        },
        "max_results": {
          "type": "integer",
          "default": 5,
          "minimum": 1,
          "maximum": 10,
          "description": "Maximum number of tool references to return. Typically 3-5 tools provide good coverage without overwhelming context."
        },
        "category_filter": {
          "type": "string",
          "description": "Optional filter to narrow search to a specific tool category (e.g., 'slack', 'github', 'database'). Leave empty to search all categories."
        }
      },
      "required": ["query"]
    }
  }
}
```

### Anthropic Tool Format

```json
{
  "name": "tool_search_tool",
  "description": "Search for tools in the tool registry to discover capabilities for completing the current task. Use this when you need functionality not provided by your currently available tools. The search queries against tool names, descriptions, and parameter information to find the most relevant tools. Returns references to matching tools which will be expanded into full definitions you can then invoke.",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "A natural language search query describing the tool capability you need. Be specific and include relevant keywords. Examples: 'send slack message to channel', 'create github pull request', 'query database for user records', 'schedule calendar meeting'"
      },
      "search_type": {
        "type": "string",
        "enum": ["semantic", "keyword", "regex"],
        "default": "semantic",
        "description": "The type of search to perform. 'semantic' uses embedding-based similarity (best for natural language queries), 'keyword' uses BM25/TF-IDF (best for exact term matching), 'regex' uses pattern matching (best for tool name patterns like 'get_*_data')"
      },
      "max_results": {
        "type": "integer",
        "default": 5,
        "description": "Maximum number of tool references to return (1-10). Typically 3-5 tools provide good coverage."
      },
      "category_filter": {
        "type": "string",
        "description": "Optional filter to narrow search to a specific tool category. Leave empty to search all categories."
      }
    },
    "required": ["query"]
  }
}
```

### Generic/Spring AI Tool Format

```json
{
  "name": "tool_search_tool",
  "description": "Search for tools in the tool registry to discover capabilities for completing the current task. Use this when you need functionality not provided by your currently available tools. The search queries against tool names, descriptions, and parameter information to find the most relevant tools. Returns references to matching tools which will be expanded into full definitions you can then invoke.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "A natural language search query describing the tool capability you need. Be specific and include relevant keywords."
      },
      "searchType": {
        "type": "string",
        "enum": ["SEMANTIC", "KEYWORD", "REGEX"],
        "description": "The type of search to perform. SEMANTIC for embedding-based similarity, KEYWORD for BM25/TF-IDF, REGEX for pattern matching."
      },
      "maxResults": {
        "type": "integer",
        "description": "Maximum number of tool references to return (1-10). Default is 5."
      },
      "categoryFilter": {
        "type": "string", 
        "description": "Optional filter to narrow search to a specific tool category."
      }
    },
    "required": ["query"]
  }
}
```

---

## Tool Search Response Format

The tool search should return a structured response containing tool references:

```json
{
  "tool_references": [
    {
      "tool_name": "slack_send_message",
      "relevance_score": 0.95,
      "summary": "Send a message to a Slack channel or user"
    },
    {
      "tool_name": "slack_send_dm",
      "relevance_score": 0.87,
      "summary": "Send a direct message to a Slack user"
    },
    {
      "tool_name": "slack_post_thread_reply",
      "relevance_score": 0.72,
      "summary": "Reply to an existing Slack thread"
    }
  ],
  "total_matches": 12,
  "search_metadata": {
    "search_type": "semantic",
    "query": "send slack message",
    "execution_time_ms": 45
  }
}
```

After receiving this response, the orchestration layer should:
1. Fetch full tool definitions for each referenced tool
2. Inject them into the LLM's context for the next turn
3. Allow the LLM to select and invoke the appropriate tool

---

## Implementation Considerations

### Search Strategies

| Strategy | Best For | Pros | Cons |
|----------|----------|------|------|
| **Semantic/Vector** | Natural language queries, fuzzy matching | Understands intent, handles synonyms | Requires embedding model, slower |
| **BM25/Keyword** | Exact term matching, known tool names | Fast, no ML dependency | Misses semantic similarity |
| **Regex** | Tool name patterns, prefix/suffix matching | Very fast, precise patterns | Limited to string patterns |

### Recommended Approach

1. **Default to Semantic Search**: Best for most use cases where users describe what they need
2. **Fall back to Keyword**: When semantic search returns no results or low confidence
3. **Use Regex**: For specific tool name patterns (e.g., `get_*`, `*_database_*`)

### Performance Optimization

1. **Pre-compute embeddings** for all tool descriptions at startup
2. **Cache search results** for common queries
3. **Keep hot tools loaded**: Always load your 3-5 most-used tools directly (no search required)
4. **Batch tool expansions**: If multiple tools are referenced, expand them all at once

### Context Window Management

| Scenario | Token Usage |
|----------|-------------|
| 50 tools loaded upfront | ~15,000-25,000 tokens |
| tool_search_tool only | ~500 tokens |
| After search (3-5 tools) | ~1,500-3,000 tokens |
| **Total savings** | **80-90%** |

---

## Example Conversation Flow

```
User: "Create a Jira ticket for the login bug and post it to the #engineering Slack channel"

LLM: [Sees only tool_search_tool in context]
     → Calls tool_search_tool(query="create jira ticket")
     
System: Returns tool_references: [jira_create_issue, jira_create_subtask]
        → Expands jira_create_issue into context

LLM: → Calls tool_search_tool(query="send slack channel message")

System: Returns tool_references: [slack_send_message, slack_post_to_channel]
        → Expands slack_send_message into context

LLM: [Now has both tools available]
     → Calls jira_create_issue(project="ENG", summary="Login bug", ...)
     
System: Returns: {ticket_id: "ENG-1234", url: "..."}

LLM: → Calls slack_send_message(channel="#engineering", text="Created Jira ticket ENG-1234...")

System: Returns: {message_id: "...", success: true}

LLM: "I've created Jira ticket ENG-1234 for the login bug and posted it to #engineering."
```

---

## Comparison with Anthropic's Implementation

| Feature | Anthropic Native | Generic Implementation |
|---------|-----------------|----------------------|
| Search Types | Regex, BM25 | Semantic, Keyword, Regex |
| `defer_loading` flag | Native API support | Managed in orchestration layer |
| Tool expansion | Automatic in API | Requires custom middleware |
| MCP Integration | Native | Via adapters |
| Prompt Caching | Native support | Requires custom implementation |

The generic implementation requires an orchestration layer (like Spring AI's Advisor pattern) to:
1. Intercept tool search results
2. Expand tool references into full definitions
3. Re-inject tools into the conversation context
4. Manage deferred vs. always-loaded tool configurations
