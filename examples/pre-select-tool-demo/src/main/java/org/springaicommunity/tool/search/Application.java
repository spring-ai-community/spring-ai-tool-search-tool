package org.springaicommunity.tool.search;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder, ToolSearcher toolSearcher) {

		return args -> {

			ChatClient chatClient = chatClientBuilder // @formatter:off
				.defaultTools(new MyTools())
				.defaultAdvisors(PreSelectToolCallAdvisor.builder()
					.toolSearcher(toolSearcher)
					.build())
				.defaultAdvisors(ToolCallAdvisor.builder().build())
				.defaultAdvisors(new MyLoggingAdvisor())
				.build();
				// @formatter:on

			var answer1 = chatClient
				.prompt("""
						Please provide short summary of the reasoning steps and required tools you would use to answer the following question:
						---
						What should I wear right now in Landsmeer, NL.
						Please suggest some budget-friendly counth shops in provided location that are open in the suggested time.
						---
						""")
				.call()
				.content();

			System.out.println("CoT Answer: " + answer1);
			var answer = chatClient.prompt(answer1).call().content();

			System.out.println(answer);
		};
	}

	static class MyTools {

		@Tool(description = "Get the current weather for a given location and at a given time")
		public String weather(String location, @ToolParam(
				description = "The time to check the weather for the given location as date-time string") String atTime) {
			return "The current weather in " + location + " is sunny with a temperature of 25°C.";
		}

		@Tool(description = "Get of clothing shops names for a given location")
		public List<String> clothing(String location,
				@ToolParam(description = "The time to check for open shops as date-time string") String openAtTime) {
			return List.of("Foo", "Bar", "Baz");
		}

		@Tool(description = "Provides the current date and time (as date-time string) for a given location")
		public String currentTime(String location) {
			return LocalDateTime.now().toString();
		}

	}

}
