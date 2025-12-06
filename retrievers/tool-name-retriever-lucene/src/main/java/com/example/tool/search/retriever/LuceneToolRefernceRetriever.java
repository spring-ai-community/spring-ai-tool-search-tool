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
import java.util.concurrent.atomic.AtomicInteger;

import com.logaritex.spring.ai.tool.search.SearchType;
import com.logaritex.spring.ai.tool.search.ToolNameReferenceRetriever;
import com.logaritex.spring.ai.tool.search.ToolSearchRequest;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse.SearchMetadata;
import com.logaritex.spring.ai.tool.search.ToolSearchResponse.ToolReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene-based tool search index for indexing and searching tool descriptions.
 * <p>
 * This class provides full-text search capabilities for tool metadata using Apache
 * Lucene's in-memory index.
 *
 * @author Christian Tzolov
 */
public class LuceneToolRefernceRetriever implements Closeable, ToolNameReferenceRetriever {

	private static final Logger logger = LoggerFactory.getLogger(LuceneToolRefernceRetriever.class);

	private static final String FIELD_ID = "id";

	private static final String FIELD_SESSION_ID = "sessionId";

	private static final String FIELD_TOOL_NAME = "toolName";

	private static final String FIELD_TOOL_DESCRIPTION = "toolDescription";

	private static final int DEFAULT_MAX_RESULTS = 10;

	private final Directory directory;

	private final IndexWriter writer;

	private final Analyzer analyzer;

	private DirectoryReader reader;

	private final float minScoreThreshold;

	private final AtomicInteger counter = new AtomicInteger(0);

	/**
	 * Creates a new LuceneToolDescriptionRepository with default settings.
	 */
	public LuceneToolRefernceRetriever() {
		this(0.25f);
	}

	public LuceneToolRefernceRetriever(float minScoreThreshold) {
		try {
			this.minScoreThreshold = minScoreThreshold;
			this.directory = new ByteBuffersDirectory();
			this.analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
			this.writer = new IndexWriter(this.directory, config);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to initialize Lucene index", e);
		}
	}

	@Override
	public void clear(String sessionId) {
		try {
			this.writer.deleteAll();
			this.writer.commit();
			if (this.reader != null) {
				this.reader.close();
				this.reader = null;
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to clear the index", e);
		}
	}

	@Override
	public SearchType searchType() {
		return SearchType.KEYWORD;
	}

	@Override
	public void addTool(String sessionId, String toolName, String toolDescription) {
		this.add(sessionId, String.valueOf(counter.getAndIncrement()), toolName, toolDescription);
	}

	@Override
	public ToolSearchResponse findTools(ToolSearchRequest toolSearchRequest) {
		return this.search(toolSearchRequest.query(),
				toolSearchRequest.maxResults() != null ? toolSearchRequest.maxResults() : DEFAULT_MAX_RESULTS,
				this.minScoreThreshold);
	}

	/**
	 * Adds a single tool to the index.
	 * 
	 * @param sessionId the session ID associated with the tool
	 * @param id unique identifier for the tool
	 * @param toolName name of the tool
	 * @param toolDescription description of the tool (searchable)
	 */
	public void add(String sessionId, String id, String toolName, String toolDescription) {
		try {
			Document doc = this.createDocument(sessionId, id, toolName, toolDescription);
			this.writer.addDocument(doc);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to add document to index", e);
		}
	}

	/**
	 * Commits all pending changes to the index. Call this after batch additions for
	 * better performance.
	 */
	public void commit() {
		try {
			this.writer.commit();
			refreshReader();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to commit changes to index", e);
		}
	}

	/**
	 * Searches for tools matching the query string with a custom result limit.
	 * @param queryString the search query
	 * @param maxResults maximum number of results to return
	 * @param minScore minimum score threshold for results
	 * @return list of matching documents
	 */
	public ToolSearchResponse search(String queryString, int maxResults, float minScore) {
		try {
			ensureReaderOpen();
			IndexSearcher searcher = new IndexSearcher(this.reader);

			Query query = buildQuery(queryString);
			if (query == null) {
				return ToolSearchResponse.builder().build();
			}

			TopDocs results = searcher.search(query, maxResults);
			return this.extractToolReferences(queryString, searcher, results, minScore);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to search index", e);
		}
	}

	private ToolSearchResponse extractToolReferences(String query, IndexSearcher searcher, TopDocs results,
			float minScore) throws IOException {

		List<ToolReference> foundToolReferences = new ArrayList<>(results.scoreDocs.length);
		StoredFields storedFields = searcher.storedFields();
		for (ScoreDoc scoreDoc : results.scoreDocs) {
			logger.info("Score: " + scoreDoc.score);
			if (scoreDoc.score >= minScore) {
				var doc = storedFields.document(scoreDoc.doc);
				foundToolReferences.add(ToolReference.builder()
					.relevanceScore(scoreDoc.score)
					.toolName(doc.get(FIELD_TOOL_NAME))
					.summary(doc.get(FIELD_TOOL_DESCRIPTION))
					.build());
			}
		}

		return ToolSearchResponse.builder()
			.toolReferences(foundToolReferences)
			.totalMatches(foundToolReferences.size())
			.searchMetadata(SearchMetadata.builder().searchType(this.searchType()).query(query).build())
			.build();
	}

	/**
	 * Deletes a tool from the index by its ID.
	 * @param id the tool ID to delete
	 */
	public void delete(String id) {
		try {
			this.writer.deleteDocuments(new Term(FIELD_ID, id));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to delete document from index", e);
		}
	}

	/**
	 * Returns the number of documents in the index.
	 * @return document count
	 */
	public int size() {
		try {
			ensureReaderOpen();
			return this.reader.numDocs();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to get index size", e);
		}
	}

	@Override
	public void close() throws IOException {
		if (this.reader != null) {
			this.reader.close();
		}
		this.writer.close();
		this.directory.close();
		this.analyzer.close();
	}

	private Document createDocument(String sessionId, String id, String toolName, String toolDescription) {
		Document doc = new Document();
		doc.add(new StringField(FIELD_SESSION_ID, sessionId, Field.Store.YES));
		doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
		doc.add(new StringField(FIELD_TOOL_NAME, toolName, Field.Store.YES));
		doc.add(new TextField(FIELD_TOOL_DESCRIPTION, toolDescription, Field.Store.YES));
		return doc;
	}

	private Query buildQuery(String queryString) {
		QueryBuilder builder = new QueryBuilder(this.analyzer);

		// Try phrase query first for exact matches
		Query phraseQuery = builder.createPhraseQuery(FIELD_TOOL_DESCRIPTION, queryString);

		// Also create a boolean query with individual terms for broader matching
		Query booleanQuery = builder.createBooleanQuery(FIELD_TOOL_DESCRIPTION, queryString,
				BooleanClause.Occur.SHOULD);

		// Combine both queries for better results
		if (phraseQuery != null && booleanQuery != null) {
			return new BooleanQuery.Builder().add(phraseQuery, BooleanClause.Occur.SHOULD)
				.add(booleanQuery, BooleanClause.Occur.SHOULD)
				.build();
		}

		return phraseQuery != null ? phraseQuery : booleanQuery;
	}

	private void ensureReaderOpen() throws IOException {
		if (this.reader == null) {
			this.writer.commit();
			this.reader = DirectoryReader.open(this.directory);
		}
		else {
			refreshReader();
		}
	}

	private void refreshReader() throws IOException {
		if (this.reader != null) {
			DirectoryReader newReader = DirectoryReader.openIfChanged(this.reader);
			if (newReader != null) {
				this.reader.close();
				this.reader = newReader;
			}
		}
	}

}
