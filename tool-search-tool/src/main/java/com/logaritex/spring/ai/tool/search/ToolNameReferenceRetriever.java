package com.logaritex.spring.ai.tool.search;

public interface ToolNameReferenceRetriever {

	SearchType searchType();
	
	void addTool(String sessionId, String toolName, String toolDescription);		

	ToolSearchResponse findTools(ToolSearchRequest toolSearchRequest);
	
	void clear(String sessionId);
}