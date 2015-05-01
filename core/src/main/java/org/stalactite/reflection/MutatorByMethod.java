package org.stalactite.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.stalactite.lang.StringAppender;

/**
 * @author mary
 */
public class MutatorByMethod<C, T> extends AbstractMutator<C, T> implements MutatorByMember<Method> {
	
	private final Method setter;
	
	public MutatorByMethod(Method setter) {
		super();
		this.setter = setter;
		this.setter.setAccessible(true);
	}
	
	@Override
	public Method getSetter() {
		return setter;
	}
	
	@Override
	protected void doSet(C c, T t) throws IllegalAccessException, InvocationTargetException {
		getSetter().invoke(c, t);
	}
	
	@Override
	protected String getSetterDescription() {
		StringAppender description = new StringAppender(100);
		description.cat(getSetter().getDeclaringClass().getName(), ".", getSetter().getName(), "(");
		Class<?>[] parameterTypes = getSetter().getParameterTypes();
		if (parameterTypes.length > 0) {
			for (Class<?> paramType : parameterTypes) {
				description.cat(paramType.getSimpleName(), ", ");
			}
			description.cutTail(2).cat(")");
		}
		return description.toString();
	}
}
