package org.springframework.ai.chat.service.invoker;

import java.lang.annotation.*;

/**
 * @author Josh Long
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
// @Reflective(HttpExchangeReflectiveProcessor.class)
public @interface ChatExchange {

	String user() default "";

	String system() default "";

}
