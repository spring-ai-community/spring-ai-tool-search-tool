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
package com.example.tool.search.retriever;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.logaritex.spring.ai.tool.search.SearchType;
import com.logaritex.spring.ai.tool.search.ToolNameReferenceRetriever;
import com.logaritex.spring.ai.tool.search.ToolSearchRequest;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse.SearchMetadata;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse.ToolReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Vector-based tool search index for semantic search of tool descriptions.
 * <p>
 * This class provides semantic search capabilities using vector embeddings, allowing for
 * more intelligent matching based on meaning rather than just keywords. Uses Spring AI's
 * SimpleVectorStore for in-memory vector storage.
 *
 * @author Christian Tzolov
 */
public class VectorToolNameReferenceRetriever implements Closeable, ToolNameReferenceRetriever {

	private static final Logger logger = LoggerFactory.getLogger(VectorToolNameReferenceRetriever.class);

	private static final String METADATA_ID = "id";

	private static final String METADATA_SESSION_ID = "sessionId";

	public static final String METADATA_TOOL_NAME = "toolName";

	public static final String METADATA_TOOL_DESCRIPTION = "toolDescription";

	private static final int DEFAULT_MAX_RESULTS = 10;

	private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.2;

	private final VectorStore vectorStore;

	private final AtomicInteger counter = new AtomicInteger(0);

	private final ConcurrentHashMap<String, List<String>> sessionToolIds = new ConcurrentHashMap<>();

	public void clear(String sessionId) {

		List<String> toolIds = sessionToolIds.get(sessionId);

		if (toolIds != null) {
			this.vectorStore.delete(toolIds);
			sessionToolIds.remove(sessionId);
			logger.info("Cleared {} tools for sessionId={}", toolIds.size(), sessionId);
		}
		else {
			logger.info("No tools found for sessionId={}", sessionId);
		}
	}

	/**
	 * Creates a new VectorToolSearchIndex with the given embedding model.
	 * @param embeddingModel the embedding model to use for vectorizing tool descriptions
	 */
	public VectorToolNameReferenceRetriever(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public SearchType searchType() {
		return SearchType.SEMANTIC;
	}

	@Override
	public void addTool(String sessionId, String toolName, String toolDescription) {
		String id = String.valueOf(counter.getAndIncrement());
		this.sessionToolIds.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(id);
		this.add(sessionId, id, toolName, toolDescription);
	}

	@Override
	public ToolSearchResponse findTools(ToolSearchRequest toolSearchRequest) {

		List<Document> docs = this.search(toolSearchRequest.query());

		List<ToolReference> toolReferences = docs.stream().map(doc -> {
			if (!doc.getMetadata().get(METADATA_SESSION_ID).equals(toolSearchRequest.sessionId())) {
				return null;
			}
			String toolName = (String) doc.getMetadata().get(METADATA_TOOL_NAME);
			Double relevanceScore = doc.getScore();
			String summary = (String) doc.getMetadata().get(METADATA_TOOL_DESCRIPTION);

			return ToolSearchResponse.ToolReference.builder()
				.toolName(toolName)
				.relevanceScore(relevanceScore)
				.summary(summary)
				.build();
		}).filter(Objects::nonNull).toList();

		return ToolSearchResponse.builder()
			.toolReferences(toolReferences)
			.totalMatches(toolReferences.size())
			.searchMetadata(
					SearchMetadata.builder().searchType(this.searchType()).query(toolSearchRequest.query()).build())
			.build();
	}

	/**
	 * Adds a single tool to the vector index.
	 * @param id unique identifier for the tool
	 * @param toolName name of the tool
	 * @param toolDescription description of the tool (used for embedding)
	 */
	public void add(String sessionId, String id, String toolName, String toolDescription) {
		Document document = new Document(id, toolDescription, Map.of(METADATA_SESSION_ID, sessionId, METADATA_ID, id,
				METADATA_TOOL_NAME, toolName, METADATA_TOOL_DESCRIPTION, toolDescription));
		this.vectorStore.add(List.of(document));
	}

	/**
	 * Searches for tools matching the query string using semantic similarity.
	 * @param queryString the search query
	 * @return list of matching documents sorted by similarity
	 */
	public List<Document> search(String queryString) {
		return search(queryString, DEFAULT_MAX_RESULTS, DEFAULT_SIMILARITY_THRESHOLD);
	}

	/**
	 * Searches for tools matching the query string with custom parameters.
	 * @param queryString the search query
	 * @param maxResults maximum number of results to return
	 * @param similarityThreshold minimum similarity threshold (0.0 to 1.0)
	 * @return list of matching documents sorted by similarity
	 */
	public List<Document> search(String queryString, int maxResults, double similarityThreshold) {
		SearchRequest searchRequest = SearchRequest.builder()
			.query(queryString)
			.topK(maxResults)
			.similarityThreshold(similarityThreshold)
			.build();

		return this.vectorStore.similaritySearch(searchRequest);
	}

	/**
	 * Deletes a tool from the index by its ID.
	 * @param id the tool ID to delete
	 */
	public void delete(String id) {
		this.vectorStore.delete(List.of(id));
	}

	/**
	 * Deletes multiple tools from the index.
	 * @param ids list of tool IDs to delete
	 */
	public void deleteAll(List<String> ids) {
		this.vectorStore.delete(ids);
	}

	@Override
	public void close() throws IOException {
		// SimpleVectorStore doesn't require explicit cleanup,
		// but this method is provided for interface consistency
		// and potential future vector store implementations that may need it
	}

	/**
	 * Gets the tool name from a search result document.
	 * @param document the search result document
	 * @return the tool name
	 */
	public static String getToolName(Document document) {
		return (String) document.getMetadata().get(METADATA_TOOL_NAME);
	}

	/**
	 * Gets the tool ID from a search result document.
	 * @param document the search result document
	 * @return the tool ID
	 */
	public static String getToolId(Document document) {
		return (String) document.getMetadata().get(METADATA_ID);
	}

	/**
	 * Gets the tool description (content) from a search result document.
	 * @param document the search result document
	 * @return the tool description
	 */
	public static String getToolDescription(Document document) {
		return document.getText();
	}

}
