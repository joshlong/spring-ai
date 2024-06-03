package org.springframework.ai.chat.service.invoker;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 *
 * Factory to create a client proxy from a {@link ChatModel} service interface with
 * {@link ChatExchange @ChatExchange} methods.
 *
 * To create an instance, use static methods to obtain a {@link Builder Builder}.
 *
 * @author Josh Long
 */
public class ChatModelServiceProxyFactory {

	public static class Builder {

		private final ChatClient.Builder clientBuilder;

		private Builder(ChatClient client) {
			this.clientBuilder = client.mutate();
		}

		private Builder(ChatModel model) {
			this.clientBuilder = ChatClient.create(model).mutate();

		}

		public ChatModelServiceProxyFactory build() {
			return new ChatModelServiceProxyFactory(this.clientBuilder.build());
		}

	}

	private final ChatClient client;

	private ChatModelServiceProxyFactory(ChatClient cc) {
		this.client = cc;
	}

	public static Builder create(ChatModel model) {
		return new Builder(model);
	}

	public static Builder create(ChatClient client) {
		return new Builder(client);
	}

	private boolean isExchangeMethod(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, ChatExchange.class);
	}

	private <S> ChatModelServiceMethod createHttpServiceMethod(Class<S> serviceType, Method method) {
		return new ChatModelServiceMethod(this.client, serviceType, method);
	}

	public <S> S createClient(Class<S> serviceType) {
		var httpServiceMethods = MethodIntrospector.selectMethods(serviceType, this::isExchangeMethod)
			.stream()
			.map(method -> createHttpServiceMethod(serviceType, method))
			.toList();
		return ProxyFactory.getProxy(serviceType, new ChatModelServiceMethodInterceptor(httpServiceMethods));
	}

}
