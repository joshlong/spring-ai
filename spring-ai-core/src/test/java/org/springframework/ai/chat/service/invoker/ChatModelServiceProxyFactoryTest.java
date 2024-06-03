package org.springframework.ai.chat.service.invoker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;

import java.util.Arrays;
import java.util.List;

class ChatModelServiceProxyFactoryTest {

	private static final String U = "return the list of actors for the movie {movie}";

	private static final String S = "you're a movie agent, that knows everything about movies.";

	@ChatExchange(system = S)
	interface Imdb {

		@ChatExchange(user = U)
		Actors getActorsFor(@UserParam String movie);

	}

	record Actors(List<Actor> actors) {
	}

	record Actor(String name) {
	}

	private final ChatOptions chatOptions = new ChatOptions() {

		@Override
		public Float getTemperature() {
			return 0f;
		}

		@Override
		public Float getTopP() {
			return 0f;
		}

		@Override
		public Integer getTopK() {
			return 0;
		}
	};

	@Test
	void fullyLoadedChatClient() throws Exception {

		var mock = Mockito.mock(ChatModel.class);
		Mockito.when(mock.call(Mockito.any(Prompt.class))).then(invocationOnMock -> {
			var args = invocationOnMock.getArguments();
			if (args[0] instanceof Prompt prompt) {
				var instructions = prompt.getInstructions();
				if (instructions.get(0) instanceof SystemMessage systemMessage) {
					var system = systemMessage.getContent();
					Assertions.assertTrue(system.contains(S));
				}
				else {
					Assertions.fail();
				}
				if (instructions.get(1) instanceof UserMessage userMessage) {
					var user = userMessage.getContent();
					var beforeProcessing = U.replace("{movie}", "Star Wars");
					Assertions.assertTrue(user.contains(beforeProcessing));
					Assertions.assertTrue(user.contains("Here is the JSON Schema instance"));
				}
				else {
					Assertions.fail();
				}

			} //
			else {
				Assertions.fail();
			}

			var json = """
					{
					 "actors":  [
					    { "name" : "Bob Hamill"} ,
					    { "name" : "Harrison McGee"}
					  ]
					 }
					""";
			return new ChatResponse(List.of(new Generation(json)));
		});
		var rr = Mockito.mock(RequestResponseAdvisor.class);
		var chatClient = ChatClient.builder(mock)
			.defaultAdvisors(rr)
			.defaultOptions(this.chatOptions)
			.defaultFunctions("a", "b", "c")
			.build();
		var invoker = ChatModelServiceProxyFactory.create(chatClient).build();
		var imdb = invoker.createClient(Imdb.class);
		var actors = imdb.getActorsFor("Star Wars");

		if (imdb instanceof Advised advised) {
			Assertions.assertEquals(1, advised.getAdvisorCount());
			Assertions.assertEquals(Imdb.class, advised.getProxiedInterfaces()[0]);

		} //
		else {
			Assertions.fail();
		}

		Assertions.assertNotNull(actors);
		Assertions.assertEquals(actors.actors().size(), 2);
		Assertions.assertEquals(actors.actors().get(0).name(), "Bob Hamill");
		Assertions.assertEquals(actors.actors().get(1).name(), "Harrison McGee");
		Assertions.assertTrue(Arrays.asList(AopProxyUtils.proxiedUserInterfaces(imdb)).contains(Imdb.class),
				"one of the proxied user interfaces is " + Imdb.class.getName());

	}

}