package org.springaicommunity.tool.search;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
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

			var toolSearchToolCallAdvisor = ToolSearchToolCallAdvisor.builder()
				.toolSearcher(toolSearcher)
				.referenceToolNameAccumulation(false)
				// .maxResults(2)
				.build();

			ChatClient chatClient = chatClientBuilder // @formatter:off			
				.defaultTools(new MyTools(), new DummyTools())
				// .defaultAdvisors(ToolCallAdvisor.builder().build())
				.defaultAdvisors(toolSearchToolCallAdvisor)
				.defaultAdvisors(new MyLoggingAdvisor())
				.build();
				// @formatter:on

			var answer = chatClient.prompt("""
					Help me plan what to wear today in Landsmeer, NL.
					Please suggest clothing shops that are open right now in the area.

					Do not make assumptions about the date, time. Use the tools for getting the current time.
					""").advisors(new TokenCounterAdvisor()).call().content();

			System.out.println(answer);
		};
	}

	static class MyTools {

		@Tool(description = "Get the weather for a given location and at a given time")
		public String weather(String location, @ToolParam(description = "YYYY-MM-DDTHH:mm:ss") String atTime) {
			return "The current weather in " + location + " is sunny with a temperature of 25°C.";
		}

		@Tool(description = "Get of clothing shops names for a given location and at a given time")
		public List<String> clothing(String location,
				@ToolParam(description = "YYYY-MM-DDTHH:mm:ss") String openAtTime) {
			return List.of("Foo", "Bar", "Baz");
		}

		@Tool(description = "Provides the current date and time (as date-time string) for a given location")
		public String currentTime(String location) {
			return LocalDateTime.now().toString();
		}

	}

}
