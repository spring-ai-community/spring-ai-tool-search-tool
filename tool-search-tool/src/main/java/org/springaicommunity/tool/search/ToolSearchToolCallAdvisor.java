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
package org.springaicommunity.tool.search;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */
public class ToolSearchToolCallAdvisor extends ToolCallAdvisor {

	/**
	 * Internal Tool implemnting the tool search functionality. It is registered,
	 * automatically, under the name "toolSearchTool".
	 */
	private final ToolCallback toolSearchToolCallback;

	/**
	 * The ToolSearcher used to find tools based on search queries.
	 */
	private final ToolSearcher toolSearcher;

	/**
	 * The Tool Search system message suffix augment to be added to the prompt during
	 * initialization.
	 */
	private final String systemMessageSuffix;

	/**
	 * If enabled, accumulates all tool search tool responses to find tool references.
	 * <p>
	 * If disabled, only the last tool search tool response is used to find tool
	 * references.
	 */
	private final boolean referenceToolNameAccumulation;

	private final Integer maxResults;

	private final Random random = new Random();

	private class ToolSearchTool {

		// @formatter:off
		@Tool(name = "toolSearchTool", description = """
				Search for tools in the tool registry to discover capabilities for completing the current task.
				Use this when you need functionality not provided by your currently available tools.
				The search queries against tool names, descriptions, and parameter information to find the most relevant tools.
				Returns references to matching tools which will be expanded into full definitions you can then invoke.
				""")
		public List<String> toolSearchTool(
			@ToolParam(description = "A natural language search query describing the tool capability you need. Be specific and include relevant keywords.") String query,
			@ToolParam(description = "Maximum number of tool references to return (1-10). Default is 5.", required = false) Integer maxResults,
		    @ToolParam(description = "Optional filter to narrow search to a specific tool category.", required = false) String categoryFilter,
			ToolContext toolContext) { // @formatter:on

			String sessionId = toolContext.getContext().get("toolSearchToolConversationId").toString();

			// User defined maxResults takes precedence over the advisor configured
			// default maxResults.s
			maxResults = (ToolSearchToolCallAdvisor.this.maxResults != null) ? ToolSearchToolCallAdvisor.this.maxResults
					: maxResults;

			ToolSearchResponse toolSearchResponse = ToolSearchToolCallAdvisor.this.toolSearcher
				.search(new ToolSearchRequest(sessionId, query, maxResults, categoryFilter));

			return toolSearchResponse.toolReferences().stream().map(tr -> tr.toolName()).toList();
		}

	}

	protected ToolSearchToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			ToolSearcher toolSearcher, String systemMessageSuffix, boolean referenceToolNameAccumulation,
			Integer maxResults) {

		super(toolCallingManager, advisorOrder);
		this.toolSearcher = toolSearcher;
		this.systemMessageSuffix = systemMessageSuffix;
		this.referenceToolNameAccumulation = referenceToolNameAccumulation;
		this.maxResults = maxResults;

		this.toolSearchToolCallback = MethodToolCallbackProvider.builder()
			.toolObjects(new ToolSearchTool())
			.build()
			.getToolCallbacks()[0];
	}

	@Override
	@SuppressWarnings("null")
	public String getName() {
		return "ToolSearchToolCallingAdvisor";
	}

	@SuppressWarnings("null")
	@Override
	protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest,
			CallAdvisorChain callAdvisorChain) {

		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {

			ConcurrentHashMap<String, ToolCallback> cachedResolvedToolCallbacks = new ConcurrentHashMap<>();

			String conversationId = this.getConversationId(chatClientRequest.context(), "default-" + random.nextInt());

			this.toolSearcher.clearIndex(conversationId);

			var toolDefinitions = this.toolCallingManager.resolveToolDefinitions(toolOptions);

			toolDefinitions.stream()
				.forEach(toolDef -> this.toolSearcher.indexTool(conversationId,
						ToolReference.builder().toolName(toolDef.name()).summary(toolDef.description()).build()));

			if (!CollectionUtils.isEmpty(toolOptions.getToolCallbacks())) {
				toolOptions.getToolCallbacks().stream().forEach(toolCallback -> {
					cachedResolvedToolCallbacks.putIfAbsent(toolCallback.getToolDefinition().name(), toolCallback);
				});
			}

			chatClientRequest.context().put("cachedToolCallbacks", cachedResolvedToolCallbacks);
			chatClientRequest.context().put("toolSearchToolConversationId", conversationId);

			return chatClientRequest.mutate()
				.prompt(chatClientRequest.prompt()
					.copy()
					.augmentSystemMessage(systemMessage -> systemMessage.copy()
						.mutate()
						.text(systemMessage.getText() + systemMessageSuffix)
						.build()))
				.build();
		}

		return super.doInitializeLoop(chatClientRequest, callAdvisorChain);
	}

	@Override
	protected ChatClientResponse doFinalizeLoop(ChatClientResponse chatClientResponse,
			CallAdvisorChain callAdvisorChain) {

		var toolSearchToolConversationId = chatClientResponse.context().get("toolSearchToolConversationId");
		if (toolSearchToolConversationId != null) {
			this.toolSearcher.clearIndex(toolSearchToolConversationId.toString());
		}

		return super.doFinalizeLoop(chatClientResponse, callAdvisorChain);
	}

	@Override
	@SuppressWarnings("null")
	protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {

		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {

			Set<ToolCallback> selectedToolCallbacks = new HashSet<>(List.of(this.toolSearchToolCallback));
			Set<String> selectedToolNames = new HashSet<>();

			var cachedToolCallbacks = (Map<String, ToolCallback>) chatClientRequest.context()
				.get("cachedToolCallbacks");

			// Find tool references from previous toolSearchTool responses.
			var conversationMessages = chatClientRequest.prompt().getInstructions();

			this.extractToolNameReferences(conversationMessages).stream().forEach(toolName -> {
				if (cachedToolCallbacks.containsKey(toolName)) {
					selectedToolCallbacks.add(cachedToolCallbacks.get(toolName));
				}
				else {
					selectedToolNames.add(toolName);
				}
			});

			// Augment the prompt with the selected tools and augmented system message.
			ToolCallingChatOptions toolOptionsCopy = toolOptions.copy();
			toolOptionsCopy.setToolCallbacks(new ArrayList<>(selectedToolCallbacks));
			toolOptionsCopy.setToolNames(selectedToolNames);

			Map<String, Object> toolContext = CollectionUtils.isEmpty(toolOptionsCopy.getToolContext())
					? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(toolOptionsCopy.getToolContext());
			toolContext.put("toolSearchToolConversationId",
					chatClientRequest.context().get("toolSearchToolConversationId"));
			toolOptionsCopy.setToolContext(toolContext);

			var augmentedChatClientRequest = chatClientRequest.mutate()
				.prompt(chatClientRequest.prompt().mutate().chatOptions(toolOptionsCopy).build())
				.build();

			return augmentedChatClientRequest;
		}

		return super.doBeforeCall(chatClientRequest, callAdvisorChain);
	}

	private List<String> extractToolNameReferences(List<Message> messages) {

		List<ToolResponse> toolSearchToolResponses = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.TOOL)
			.map(r -> ((ToolResponseMessage) r).getResponses())
			.flatMap(List::stream)
			.filter(r -> r.name().equalsIgnoreCase(this.toolSearchToolCallback.getToolDefinition().name()))
			.toList();

		if (CollectionUtils.isEmpty(toolSearchToolResponses)) {
			return List.of();
		}

		toolSearchToolResponses = (this.referenceToolNameAccumulation) ? toolSearchToolResponses
				: List.of(toolSearchToolResponses.get(toolSearchToolResponses.size() - 1));

		return toolSearchToolResponses.stream()
			.map(r -> JsonParser.fromJson(r.responseData(), new TypeReference<List<String>>() {
			}))
			.flatMap(List::stream)
			.toList();
	}

	private String getConversationId(Map<String, Object> context, String defaultConversationId) {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");

		return context.containsKey(ChatMemory.CONVERSATION_ID) ? context.get(ChatMemory.CONVERSATION_ID).toString()
				: defaultConversationId;
	}

	/**
	 * Creates a new Builder instance for constructing a ToolSearchToolCallAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for creating instances of ToolSearchToolCallAdvisor.
	 * <p>
	 * This builder extends {@link ToolCallAdvisor.Builder} and adds configuration options
	 * specific to tool search functionality.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> extends ToolCallAdvisor.Builder<T> {

		private ToolSearcher toolSearcher;

		private String systemMessageSuffix;

		private boolean referenceToolNameAccumulation = true;

		private Integer maxResults;

		protected Builder() {
		}

		public T referenceToolNameAccumulation(boolean referenceToolNameAccumulation) {
			this.referenceToolNameAccumulation = referenceToolNameAccumulation;
			return self();
		}

		public T systemMessageSuffix(String systemMessageSuffix) {
			Assert.hasText(systemMessageSuffix, "systemMessageSuffix cannot be null or empty");
			this.systemMessageSuffix = systemMessageSuffix;
			return self();
		}

		/**
		 * Sets the ToolSearcher to be used for finding tools.
		 * @param toolSearcher the ToolSearcher instance
		 * @return this Builder instance for method chaining
		 */
		public T toolSearcher(ToolSearcher toolSearcher) {
			Assert.notNull(toolSearcher, "toolSearcher cannot be null");
			this.toolSearcher = toolSearcher;
			return self();
		}

		/**
		 * Sets the maximum number of tool references to return in tool search results.
		 * This is the human/user defined default value used when invoking the tool search
		 * tool.
		 * @param maxResults
		 * @return
		 */
		public T maxResults(Integer maxResults) {
			this.maxResults = maxResults;
			return self();
		}

		/**
		 * Builds and returns a new ToolSearchToolCallAdvisor instance with the configured
		 * properties.
		 * @return a new ToolSearchToolCallAdvisor instance
		 * @throws IllegalArgumentException if required parameters are null or invalid
		 */
		@Override
		public ToolSearchToolCallAdvisor build() {

			if (!StringUtils.hasText(this.systemMessageSuffix)) {
				try {
					this.systemMessageSuffix = new DefaultResourceLoader()
						.getResource("classpath:/DEFAULT_SYSTEM_PROMPT_SUFFIX.md")
						.getContentAsString(Charset.defaultCharset());
				}
				catch (Exception ex) {
					throw new IllegalArgumentException(
							"Failed to load default system message suffix from classpath resource", ex);
				}
			}

			if (this.toolSearcher == null) {
				throw new IllegalArgumentException("toolSearcher cannot be null");
			}

			return new ToolSearchToolCallAdvisor(getToolCallingManager(), getAdvisorOrder(), this.toolSearcher,
					this.systemMessageSuffix, this.referenceToolNameAccumulation, this.maxResults);
		}

	}

}
