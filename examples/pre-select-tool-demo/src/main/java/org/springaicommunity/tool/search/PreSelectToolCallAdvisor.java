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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springaicommunity.tool.search.ToolReference;
import org.springaicommunity.tool.search.ToolSearchRequest;
import org.springaicommunity.tool.search.ToolSearchResponse;
import org.springaicommunity.tool.search.ToolSearcher;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
@SuppressWarnings("null")
public class PreSelectToolCallAdvisor extends ToolCallAdvisor {

	private final ToolSearcher toolSearcher;

	private final Random random = new Random();

	protected PreSelectToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			ToolSearcher toolSearcher) {

		super(toolCallingManager, advisorOrder);
		this.toolSearcher = toolSearcher;
	}

	@Override
	public String getName() {
		return "PreSelectToolCallingAdvisor";
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

			return chatClientRequest;
		}

		return super.doInitializeLoop(chatClientRequest, callAdvisorChain);
	}

	@Override
	protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {

		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {

			// Find tool references from previous toolSearchTool responses.
			String userMessagesText = chatClientRequest.prompt()
				.getUserMessages()
				.stream()
				.map(m -> m.getText())
				.collect(Collectors.joining("\n"));

			String sessionId = chatClientRequest.context().get("toolSearchToolConversationId").toString();

			ToolSearchResponse toolSearchResponse = this.toolSearcher
				.search(new ToolSearchRequest(sessionId, userMessagesText, null, null));

			Set<ToolCallback> selectedToolCallbacks = new HashSet<>();
			Set<String> selectedToolNames = new HashSet<>();
			var cachedToolCallbacks = (Map<String, ToolCallback>) chatClientRequest.context()
				.get("cachedToolCallbacks");

			toolSearchResponse.toolReferences().forEach(toolReference -> {
				if (cachedToolCallbacks.containsKey(toolReference.toolName())) {
					selectedToolCallbacks.add(cachedToolCallbacks.get(toolReference.toolName()));
				}
				else {
					selectedToolNames.add(toolReference.toolName());
				}
			});

			// Augment the prompt with the selected tools and augmented system
			// message.
			ToolCallingChatOptions toolOptionsCopy = toolOptions.copy();

			toolOptionsCopy.setToolCallbacks(new ArrayList<>(selectedToolCallbacks));
			toolOptionsCopy.setToolNames(selectedToolNames);

			var augmentedChatClientRequest = chatClientRequest.mutate()
				.prompt(chatClientRequest.prompt().mutate().chatOptions(toolOptionsCopy).build())
				.build();

			return augmentedChatClientRequest;
			// }
		}

		return super.doBeforeCall(chatClientRequest, callAdvisorChain);
	}

	private String getConversationId(Map<String, Object> context, String defaultConversationId) {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");

		return context.containsKey(ChatMemory.CONVERSATION_ID) ? context.get(ChatMemory.CONVERSATION_ID).toString()
				: defaultConversationId;
	}

	/**
	 * Creates a new Builder instance for constructing a PreSelectToolCallAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for creating instances of PreSelectToolCallAdvisor.
	 * <p>
	 * This builder extends {@link ToolCallAdvisor.Builder} and adds configuration options
	 * specific to tool search functionality.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> extends ToolCallAdvisor.Builder<T> {

		private ToolSearcher toolSearcher;

		protected Builder() {
		}

		/**
		 * Sets the ToolSearcher to be used for finding tools.
		 * @param toolSearcher the ToolSearcher instance
		 * @return this Builder instance for method chaining
		 */
		public T toolSearcher(ToolSearcher toolSearcher) {
			this.toolSearcher = toolSearcher;
			return self();
		}

		/**
		 * Builds and returns a new PreSelectToolCallAdvisor instance with the configured
		 * properties.
		 * @return a new PreSelectToolCallAdvisor instance
		 * @throws IllegalArgumentException if required parameters are null or invalid
		 */
		@Override
		public PreSelectToolCallAdvisor build() {
			return new PreSelectToolCallAdvisor(getToolCallingManager(), getAdvisorOrder(), this.toolSearcher);
		}

	}

}
