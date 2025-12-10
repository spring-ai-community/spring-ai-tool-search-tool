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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolSearchToolCallAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ToolSearchToolCallAdvisorTests {

	@Mock
	private ToolCallingManager toolCallingManager;

	@Mock
	private ToolSearcher toolSearcher;

	@Test
	void whenToolSearcherIsNullThenThrow() {
		assertThatThrownBy(() -> ToolSearchToolCallAdvisor.builder().build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testBuilderMethodChaining() {
		ToolSearcher customSearcher = mock(ToolSearcher.class);
		ToolCallingManager customManager = mock(ToolCallingManager.class);
		int customOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 500;
		String customSuffix = "\n\nCustom suffix";

		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(customManager)
			.advisorOrder(customOrder)
			.toolSearcher(customSearcher)
			.systemMessageSuffix(customSuffix)
			.referenceToolNameAccumulation(false)
			.maxResults(10)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("ToolSearchToolCallingAdvisor");
	}

	@Test
	void testDefaultValues() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder().toolSearcher(this.toolSearcher).build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 300);
		assertThat(advisor.getName()).isEqualTo("ToolSearchToolCallingAdvisor");
	}

	@Test
	void testInitializeLoopIndexesTools() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.systemMessageSuffix("\n\nTest suffix")
			.build();

		// Create mock tool definitions
		ToolDefinition toolDef1 = DefaultToolDefinition.builder()
			.name("tool1")
			.description("Description for tool1")
			.inputSchema("{}")
			.build();
		ToolDefinition toolDef2 = DefaultToolDefinition.builder()
			.name("tool2")
			.description("Description for tool2")
			.inputSchema("{}")
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(toolDef1, toolDef2));

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify that indexTool was called for each tool definition
		ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<ToolReference> toolRefCaptor = ArgumentCaptor.forClass(ToolReference.class);

		verify(this.toolSearcher, times(2)).indexTool(sessionIdCaptor.capture(), toolRefCaptor.capture());

		List<ToolReference> indexedTools = toolRefCaptor.getAllValues();
		assertThat(indexedTools).hasSize(2);
		assertThat(indexedTools.get(0).toolName()).isEqualTo("tool1");
		assertThat(indexedTools.get(1).toolName()).isEqualTo("tool2");
	}

	@Test
	void testInitializeLoopAugmentsSystemMessage() {
		String customSuffix = "\n\nCUSTOM SUFFIX FOR TESTING";
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.systemMessageSuffix(customSuffix)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		SystemMessage originalSystemMessage = new SystemMessage("Original system message");
		UserMessage userMessage = new UserMessage("test");

		ToolCallingChatOptions toolOptions = mock(ToolCallingChatOptions.class,
				Mockito.withSettings().strictness(Strictness.LENIENT));
		when(toolOptions.copy()).thenReturn(toolOptions);
		when(toolOptions.getInternalToolExecutionEnabled()).thenReturn(true);

		Prompt prompt = new Prompt(List.of(originalSystemMessage, userMessage), toolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientResponse response = createMockResponse(false);

		// Capture the augmented request
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify system message was augmented
		assertThat(capturedRequest[0]).isNotNull();
		List<Message> messages = capturedRequest[0].prompt().getInstructions();
		SystemMessage augmentedSystemMessage = (SystemMessage) messages.get(0);
		assertThat(augmentedSystemMessage.getText()).contains("Original system message");
		assertThat(augmentedSystemMessage.getText()).contains(customSuffix);
	}

	@Test
	void testFinalizeLoopClearsIndex() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify clearIndex was called
		verify(this.toolSearcher, times(2)).clearIndex(anyString());
	}

	@Test
	void testBeforeCallExtractsToolReferences() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		// Create a request with tool response messages containing tool references
		ToolResponseMessage.ToolResponse toolSearchResponse = new ToolResponseMessage.ToolResponse("id1",
				"toolSearchTool", "[\"weatherTool\", \"calculatorTool\"]");
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolSearchResponse))
			.build();

		SystemMessage systemMessage = new SystemMessage("System message");
		UserMessage userMessage = new UserMessage("test");
		AssistantMessage assistantMessage = AssistantMessage.builder().content("Using tool search").build();

		// Use real TestToolCallingChatOptions instead of mocking
		TestToolCallingChatOptions toolOptions = new TestToolCallingChatOptions();

		Prompt prompt = new Prompt(List.of(systemMessage, userMessage, assistantMessage, toolResponseMessage),
				toolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientResponse response = createMockResponse(false);

		// Capture the request to verify tool names were extracted
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify tool names were extracted
		assertThat(capturedRequest[0]).isNotNull();
		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();
		// The tool search tool callback should be present
		assertThat(capturedOptions.getToolCallbacks()).hasSize(1);
		assertThat(capturedOptions.getToolCallbacks().get(0).getToolDefinition().name()).isEqualTo("toolSearchTool");
		assertThat(capturedOptions.getToolNames()).contains("weatherTool", "calculatorTool");
	}

	@Test
	void testConversationIdFromContext() {
		String expectedConversationId = "test-conversation-123";
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		Map<String, Object> context = new ConcurrentHashMap<>();
		context.put(ChatMemory.CONVERSATION_ID, expectedConversationId);

		ChatClientRequest request = createMockRequest(true);
		request = request.mutate().context(context).build();

		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify that the expected conversation ID was used
		// clearIndex is called twice: once in doInitializeLoop and once in doFinalizeLoop
		ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.toolSearcher, times(2)).clearIndex(sessionIdCaptor.capture());

		// Both calls should use the same conversation ID
		List<String> capturedIds = sessionIdCaptor.getAllValues();
		assertThat(capturedIds).hasSize(2);
		assertThat(capturedIds.get(0)).isEqualTo(expectedConversationId);
		assertThat(capturedIds.get(1)).isEqualTo(expectedConversationId);
	}

	@Test
	void testGetName() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder().toolSearcher(this.toolSearcher).build();
		assertThat(advisor.getName()).isEqualTo("ToolSearchToolCallingAdvisor");
	}

	@Test
	void testToolSearchToolCallbackIsRegistered() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.build();

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class))).thenReturn(List.of());

		ChatClientRequest request = createMockRequest(true);
		ChatClientResponse response = createMockResponse(false);

		// Capture the request to verify toolSearchTool callback is present
		ChatClientRequest[] capturedRequest = new ChatClientRequest[1];
		CallAdvisor capturingAdvisor = new TerminalCallAdvisor((req, chain) -> {
			capturedRequest[0] = req;
			return response;
		});

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, capturingAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		assertThat(capturedRequest[0]).isNotNull();
		ToolCallingChatOptions capturedOptions = (ToolCallingChatOptions) capturedRequest[0].prompt().getOptions();
		assertThat(capturedOptions.getToolCallbacks()).isNotEmpty();

		// Find the toolSearchTool callback
		boolean foundToolSearchTool = capturedOptions.getToolCallbacks()
			.stream()
			.anyMatch(callback -> "toolSearchTool".equals(callback.getToolDefinition().name()));

		assertThat(foundToolSearchTool).isTrue();
	}

	@Test
	void testCachedToolCallbacksAreUsed() {
		ToolSearchToolCallAdvisor advisor = ToolSearchToolCallAdvisor.builder()
			.toolCallingManager(this.toolCallingManager)
			.toolSearcher(this.toolSearcher)
			.build();

		// Create a mock tool callback
		ToolCallback mockToolCallback = mock(ToolCallback.class);
		ToolDefinition mockToolDef = DefaultToolDefinition.builder()
			.name("weatherTool")
			.description("Gets weather")
			.inputSchema("{}")
			.build();
		when(mockToolCallback.getToolDefinition()).thenReturn(mockToolDef);

		// Use real TestToolCallingChatOptions with the tool callback configured
		TestToolCallingChatOptions toolOptions = new TestToolCallingChatOptions();
		toolOptions.setToolCallbacks(List.of(mockToolCallback));

		when(this.toolCallingManager.resolveToolDefinitions(any(ToolCallingChatOptions.class)))
			.thenReturn(List.of(mockToolDef));

		// Create a request with tool response message referencing the weatherTool
		ToolResponseMessage.ToolResponse toolSearchResponse = new ToolResponseMessage.ToolResponse("id1",
				"toolSearchTool", "[\"weatherTool\"]");
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolSearchResponse))
			.build();

		UserMessage userMessage = new UserMessage("test");

		Prompt prompt = new Prompt(List.of(userMessage, toolResponseMessage), toolOptions);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientResponse response = createMockResponse(false);

		CallAdvisor terminalAdvisor = new TerminalCallAdvisor((req, chain) -> response);

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		advisor.adviseCall(request, realChain);

		// Verify the tool was indexed
		verify(this.toolSearcher, times(1)).indexTool(anyString(), any(ToolReference.class));
	}

	// Helper methods

	private ChatClientRequest createMockRequest(boolean withToolCallingOptions) {
		List<Message> instructions = List.of(new SystemMessage("System message"), new UserMessage("test message"));

		ChatOptions options = null;
		if (withToolCallingOptions) {
			// Use a real TestToolCallingChatOptions instead of mocking to avoid Byte
			// Buddy issues on Java 25
			options = new TestToolCallingChatOptions();
		}

		Prompt prompt = new Prompt(instructions, options);

		return ChatClientRequest.builder().prompt(prompt).build();
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls, Map<String, Object> context) {
		// Create real objects instead of mocking to avoid Byte Buddy issues with Java 25
		AssistantMessage assistantMessage;
		if (hasToolCalls) {
			// Create an assistant message with a tool call to make hasToolCalls() return
			// true
			assistantMessage = AssistantMessage.builder()
				.content("response")
				.toolCalls(List.of(new AssistantMessage.ToolCall("id1", "tool", "toolName", "{}")))
				.build();
		}
		else {
			assistantMessage = new AssistantMessage("response");
		}
		Generation generation = new Generation(assistantMessage);

		// Create a real ChatResponse - hasToolCalls() is derived from generations' tool
		// calls
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of(generation)).build();

		// Create a real ChatClientResponse using the builder with context from request
		return ChatClientResponse.builder()
			.chatResponse(chatResponse)
			.context(context != null ? context : new ConcurrentHashMap<>())
			.build();
	}

	private ChatClientResponse createMockResponse(boolean hasToolCalls) {
		return createMockResponse(hasToolCalls, new ConcurrentHashMap<>());
	}

	private static class TerminalCallAdvisor implements CallAdvisor {

		private final BiFunction<ChatClientRequest, CallAdvisorChain, ChatClientResponse> responseFunction;

		TerminalCallAdvisor(BiFunction<ChatClientRequest, CallAdvisorChain, ChatClientResponse> responseFunction) {
			this.responseFunction = responseFunction;
		}

		@Override
		public String getName() {
			return "terminal";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
			ChatClientResponse response = this.responseFunction.apply(req, chain);
			// Ensure the response has the context from the request (including
			// cachedToolCallbacks, etc.)
			Map<String, Object> mergedContext = new ConcurrentHashMap<>(req.context());
			mergedContext.putAll(response.context());
			return response.mutate().context(mergedContext).build();
		}

	}

	/**
	 * Simple test implementation of ToolCallingChatOptions to avoid Mockito/ByteBuddy
	 * issues on Java 25. Implements the required methods with sensible defaults.
	 */
	private static class TestToolCallingChatOptions implements ToolCallingChatOptions {

		private boolean internalToolExecutionEnabled = true;

		private List<ToolCallback> toolCallbacks = new java.util.ArrayList<>();

		private java.util.Set<String> toolNames = new java.util.HashSet<>();

		private Map<String, Object> toolContext = new java.util.HashMap<>();

		@Override
		public List<ToolCallback> getToolCallbacks() {
			return this.toolCallbacks;
		}

		@Override
		public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks != null ? toolCallbacks : new java.util.ArrayList<>();
		}

		@Override
		public java.util.Set<String> getToolNames() {
			return this.toolNames;
		}

		@Override
		public void setToolNames(java.util.Set<String> toolNames) {
			this.toolNames = toolNames != null ? toolNames : new java.util.HashSet<>();
		}

		@Override
		public Boolean getInternalToolExecutionEnabled() {
			return this.internalToolExecutionEnabled;
		}

		@Override
		public void setInternalToolExecutionEnabled(Boolean enabled) {
			this.internalToolExecutionEnabled = enabled != null ? enabled : true;
		}

		@Override
		public Map<String, Object> getToolContext() {
			return this.toolContext;
		}

		@Override
		public void setToolContext(Map<String, Object> toolContext) {
			this.toolContext = toolContext != null ? toolContext : new java.util.HashMap<>();
		}

		@Override
		public TestToolCallingChatOptions copy() {
			TestToolCallingChatOptions copy = new TestToolCallingChatOptions();
			copy.internalToolExecutionEnabled = this.internalToolExecutionEnabled;
			copy.toolCallbacks = new java.util.ArrayList<>(this.toolCallbacks);
			copy.toolNames = new java.util.HashSet<>(this.toolNames);
			copy.toolContext = new java.util.HashMap<>(this.toolContext);
			return copy;
		}

		// ChatOptions methods - return null or defaults for unused options
		@Override
		public String getModel() {
			return null;
		}

		@Override
		public Double getFrequencyPenalty() {
			return null;
		}

		@Override
		public Integer getMaxTokens() {
			return null;
		}

		@Override
		public Double getPresencePenalty() {
			return null;
		}

		@Override
		public List<String> getStopSequences() {
			return null;
		}

		@Override
		public Double getTemperature() {
			return null;
		}

		@Override
		public Integer getTopK() {
			return null;
		}

		@Override
		public Double getTopP() {
			return null;
		}

	}

}
