package org.springframework.ai.chat.service.invoker;

import java.lang.annotation.*;

/**
 * @author Josh Long
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SystemParam {

	String value() default "";

}