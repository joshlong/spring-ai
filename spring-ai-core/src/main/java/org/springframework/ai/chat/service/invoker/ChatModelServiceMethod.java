package org.springframework.ai.chat.service.invoker;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Josh Long
 */
class ChatModelServiceMethod {

	private final ChatClient chatClient;

	private final Class<?> containingClass;

	private final Method method;

	<S> ChatModelServiceMethod(ChatClient chatClient, Class<?> containingClass, Method method) {
		this.chatClient = chatClient;
		this.containingClass = containingClass;
		this.method = method;
	}

	public Method getMethod() {
		return this.method;
	}

	private static String pickHigherPriorityValueFromAnnotation(ChatExchange type, ChatExchange method,
			Function<ChatExchange, String> mapper) {
		var t = type == null ? null : mapper.apply(type);
		var m = method == null ? null : mapper.apply(method);
		if (StringUtils.hasText(m))
			return m;
		if (StringUtils.hasText(t))
			return t;
		return null;
	}

	private static ChatExchange getChatExchangeFor(List<AnnotationDescriptor> methods) {
		if (methods == null || methods.isEmpty())
			return null;
		var first = methods.get(0);
		return first.exchange;
	}

	@Nullable
	public Object invoke(Object[] arguments) {
		var typeExchange = getChatExchangeFor(AnnotationDescriptor.getAnnotationDescriptors(this.containingClass));
		var methodExchange = getChatExchangeFor(AnnotationDescriptor.getAnnotationDescriptors(this.method));
		var user = pickHigherPriorityValueFromAnnotation(typeExchange, methodExchange, ChatExchange::user);
		var system = pickHigherPriorityValueFromAnnotation(typeExchange, methodExchange, ChatExchange::system);

		Assert.state(this.method.getParameterCount() == arguments.length,
				"the arguments must match the parameter count");

		var userParams = getParamsFor(this.method, UserParam.class, arguments);
		var systemParams = getParamsFor(this.method, SystemParam.class, arguments);

		var ccb = this.chatClient.prompt();

		if (StringUtils.hasText(user))
			ccb = ccb.user(spec -> spec.text(user).params(userParams));

		if (StringUtils.hasText(system))
			ccb = ccb.system(spec -> spec.text(system).params(systemParams));

		return ccb.call().entity(method.getReturnType());
	}

	private static Map<String, Object> getParamsFor(Method method, Class<? extends Annotation> annotation,
			Object[] arguments) {
		var mapOfParams = new HashMap<String, Object>();
		var ctr = 0;
		for (var param : method.getParameters()) {
			if (param.isAnnotationPresent(annotation)) {
				var paramName = param.getName();
				var arg = arguments[ctr];
				mapOfParams.put(paramName, arg);
				ctr += 1;
			}
		}
		return mapOfParams;
	}

	private static class AnnotationDescriptor {

		static List<AnnotationDescriptor> getAnnotationDescriptors(AnnotatedElement element) {
			return MergedAnnotations//
				.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())//
				.stream(ChatExchange.class)//
				.filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))//
				.map(AnnotationDescriptor::new)//
				.distinct()//
				.toList();
		}

		private final ChatExchange exchange;

		private final MergedAnnotation<?> root;

		AnnotationDescriptor(MergedAnnotation<ChatExchange> mergedAnnotation) {
			this.exchange = mergedAnnotation.synthesize();
			this.root = mergedAnnotation.getRoot();
		}

		@Override
		public boolean equals(Object obj) {
			return (obj instanceof AnnotationDescriptor that && this.exchange.equals(that.exchange));
		}

		@Override
		public int hashCode() {
			return this.exchange.hashCode();
		}

		@Override
		public String toString() {
			return this.root.synthesize().toString();
		}

	}

}
