package org.springframework.ai.chat.service.invoker;

import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Josh Long
 */
public class ChatModelServiceProxyFactory {

	public static class Builder {

		private final ChatModel chatModel;

		private final List<RequestResponseAdvisor> advisors = new ArrayList<>();

		private final List<String> functionNames = new ArrayList<>();

		private Builder(ChatModel chatModel) {
			this.chatModel = chatModel;
		}

		public Builder advisors(RequestResponseAdvisor... questionAnswerAdvisor) {
			this.advisors.addAll(Arrays.asList(questionAnswerAdvisor));
			return this;
		}

		public Builder functions(String... functionNames) {
			this.functionNames.addAll(Arrays.asList(functionNames));
			return this;
		}

		public ChatModelServiceProxyFactory build() {
			return new ChatModelServiceProxyFactory(this.chatModel, this.advisors, this.functionNames);
		}

	}

	private final ChatModel chatModel;

	private final List<RequestResponseAdvisor> advisors = new ArrayList<>();

	private final List<String> functionNames = new ArrayList<>();

	private ChatModelServiceProxyFactory(ChatModel model, List<RequestResponseAdvisor> advisors,
			List<String> functionNames) {
		this.chatModel = model;
		this.advisors.addAll(advisors);
		this.functionNames.addAll(functionNames);
	}

	public static Builder create(ChatModel model) {
		return new Builder(model);
	}

	private boolean isExchangeMethod(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, ChatExchange.class);
	}

	private <S> ChatModelServiceMethod createHttpServiceMethod(Class<S> serviceType, Method method) {
		return new ChatModelServiceMethod(this.chatModel, serviceType, method);
	}

	public <S> S createClient(Class<S> serviceType) {
		var httpServiceMethods = MethodIntrospector.selectMethods(serviceType, this::isExchangeMethod)
			.stream()
			.map(method -> createHttpServiceMethod(serviceType, method))
			.toList();
		return ProxyFactory.getProxy(serviceType, new ChatModelServiceMethodInterceptor(httpServiceMethods));
	}

}
