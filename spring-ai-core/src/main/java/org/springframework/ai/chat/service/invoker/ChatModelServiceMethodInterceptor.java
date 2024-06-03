package org.springframework.ai.chat.service.invoker;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link MethodInterceptor} that invokes an {@link ChatModelServiceMethod}.
 *
 * @author Josh Long
 */
class ChatModelServiceMethodInterceptor implements MethodInterceptor {

	private final Map<Method, ChatModelServiceMethod> serviceMethods;

	ChatModelServiceMethodInterceptor(List<ChatModelServiceMethod> methods) {
		this.serviceMethods = methods//
			.stream()//
			.collect(Collectors.toMap(ChatModelServiceMethod::getMethod, Function.identity()));
	}

	@Override
	@Nullable
	public Object invoke(MethodInvocation invocation) throws Throwable {
		var method = invocation.getMethod();
		var serviceMethod = this.serviceMethods.get(method);
		if (serviceMethod != null) {
			var arguments = KotlinDetector//
				.isSuspendingFunction(method) ? //
						resolveCoroutinesArguments(invocation.getArguments()) : invocation.getArguments();
			return serviceMethod.invoke(arguments);
		}
		if (method.isDefault()) {
			if (invocation instanceof ReflectiveMethodInvocation reflectiveMethodInvocation) {
				var proxy = reflectiveMethodInvocation.getProxy();
				return InvocationHandler.invokeDefault(proxy, method, invocation.getArguments());
			}
		}
		throw new IllegalStateException("Unexpected method invocation: " + method);
	}

	private static Object[] resolveCoroutinesArguments(Object[] args) {
		var functionArgs = new Object[args.length - 1];
		System.arraycopy(args, 0, functionArgs, 0, args.length - 1);
		return functionArgs;
	}

}
