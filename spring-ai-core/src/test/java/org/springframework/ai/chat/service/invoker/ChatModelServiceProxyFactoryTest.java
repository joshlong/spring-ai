package org.springframework.ai.chat.service.invoker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.messages.Message;
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

	interface Riddler {

		@ChatExchange(system = "give me a joke about ${batman.villains.category}")
		Riddle riddle();

	}

	record Riddle(String question) {
	}

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
	void propertyPlaceholderResolution() throws Exception {
		var mock = Mockito.mock(ChatModel.class);

		var chatResponsePromptAnswer = new PromptProcessingAnswer<ChatResponse>() {

			@Override
			ChatResponse examine(Prompt p) throws Exception {
				return new ChatResponse(List.of(new Generation("")));
			}
		};
		Mockito.when(mock.call(Mockito.any(Prompt.class))).then(chatResponsePromptAnswer);

	}

	/**
	 * make it easy to poke at the body of the {@link Prompt }
	 */
	static abstract class PromptProcessingAnswer<T> implements Answer<T> {

		abstract T examine(Prompt p) throws Exception;

		@Override
		public T answer(InvocationOnMock invocationOnMock) throws Throwable {
			var args = invocationOnMock.getArguments();
			if (args[0] instanceof Prompt prompt) {
				return this.examine(prompt);
			} //

			Assertions.fail("the first argument is not a " + Prompt.class.getName());
			return null;
		}

	}

	@Test
	void fullyLoadedChatClient() {
		var mock = Mockito.mock(ChatModel.class);
		var chatResponsePromptAnswer = new PromptProcessingAnswer<ChatResponse>() {

			@Override
			ChatResponse examine(Prompt prompt) throws Exception {
				var instructions = prompt.getInstructions();
				if (instructions.get(0) instanceof SystemMessage systemMessage) {
					var system = systemMessage.getContent();
					Assertions.assertTrue(system.contains(S));
				}
				else {
					Assertions.fail("the first " + Message.class.getName() + " should be an instance of "
							+ SystemMessage.class.getName());
				}
				if (instructions.get(1) instanceof UserMessage userMessage) {
					var user = userMessage.getContent();
					var beforeProcessing = U.replace("{movie}", "Star Wars");
					Assertions.assertTrue(user.contains(beforeProcessing));
					Assertions.assertTrue(user.contains("Here is the JSON Schema instance"));
				}
				else {
					Assertions.fail("the second " + Message.class.getName() + " should be an instance of "
							+ UserMessage.class.getName());
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
			}
		};
		Mockito.when(mock.call(Mockito.any(Prompt.class))).then(chatResponsePromptAnswer);
		var requestResponseAdvisor = Mockito.mock(RequestResponseAdvisor.class);
		var chatClient = ChatClient.builder(mock)
			.defaultAdvisors(requestResponseAdvisor)
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
			Assertions.fail("the " + Imdb.class.getName() + "instance is not an instance of Advised");
		}

		Assertions.assertNotNull(actors);
		Assertions.assertEquals(actors.actors().size(), 2);
		Assertions.assertEquals(actors.actors().get(0).name(), "Bob Hamill");
		Assertions.assertEquals(actors.actors().get(1).name(), "Harrison McGee");
		Assertions.assertTrue(Arrays.asList(AopProxyUtils.proxiedUserInterfaces(imdb)).contains(Imdb.class),
				"one of the proxied user interfaces is " + Imdb.class.getName());

	}

}