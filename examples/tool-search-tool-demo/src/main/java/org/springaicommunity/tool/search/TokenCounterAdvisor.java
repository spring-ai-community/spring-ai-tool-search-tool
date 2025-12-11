package org.springaicommunity.tool.search;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;

public class TokenCounterAdvisor implements BaseAdvisor {

	AtomicInteger totalTokenCouner = new AtomicInteger(0);

	AtomicInteger promptTokenCouner = new AtomicInteger(0);

	AtomicInteger completionTokenCouner = new AtomicInteger(0);

	AtomicInteger requestCouner = new AtomicInteger(0);

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1000;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		var usage = chatClientResponse.chatResponse().getMetadata().getUsage();

		requestCouner.incrementAndGet();
		totalTokenCouner.addAndGet(usage.getTotalTokens());
		promptTokenCouner.addAndGet(usage.getPromptTokens());
		completionTokenCouner.addAndGet(usage.getCompletionTokens());

		System.out.println("Current TOKENS Total: " + usage.getTotalTokens() + ", Completion: "
				+ usage.getCompletionTokens() + ", Prompt: " + usage.getPromptTokens());

		System.out.println(
				"Accumulated TOKENS Total: " + totalTokenCouner.get() + ", Completion: " + completionTokenCouner.get()
						+ ", Prompt: " + promptTokenCouner.get() + ", Number of requests: " + requestCouner.get());

		return chatClientResponse;
	}

}