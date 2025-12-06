/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.logaritex.spring.ai.tool.search;

/**
 * Interface for searching and discovering tools on-demand.
 * <p>
 * Implementations provide different search strategies (keyword-based, semantic, regex)
 * to find relevant tools from a registered catalog based on search queries.
 * This aligns with Anthropic's Tool Search Tool concept for dynamic tool discovery.
 * 
 * @author Christian Tzolov
 */
public interface ToolSearcher {

	/**
	 * Returns the type of search this implementation provides.
	 * @return the search type (KEYWORD, SEMANTIC, or REGEX)
	 */
	SearchType searchType();
	
	/**
	 * Registers a tool in the search index for the specified session.
	 * @param sessionId the session identifier for tool isolation
	 * @param toolName the unique name of the tool
	 * @param toolDescription the description of the tool (used for search matching)
	 */
	void indexTool(String sessionId, String toolName, String toolDescription);		

	/**
	 * Searches for tools matching the given request criteria.
	 * @param toolSearchRequest the search request containing query and parameters
	 * @return a response containing matching tool references
	 */
	ToolSearchResponse search(ToolSearchRequest toolSearchRequest);
	
	/**
	 * Clears all indexed tools for the specified session.
	 * @param sessionId the session identifier
	 */
	void clearIndex(String sessionId);
}
