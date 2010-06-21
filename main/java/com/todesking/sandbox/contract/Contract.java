package com.todesking.sandbox.contract;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.commons.lang.ArrayUtils;

public class Contract<T> {
	protected T target() {
		return target;
	}

	private T target;

	protected void contract(String message, boolean condition) {
		if (!condition)
			throw new ContractError("Contract error: " + message);
	}

	public static <K, T extends K> K checkerForImplementation(Class<K> klass,
			final T target) {
		final Class<? extends Contract<K>> contractClass =
			findContractClass(klass, "ContractForImplement");
		return createChecker(klass, target, contractClass, false);
	}

	public static <K, T extends K> K checkerForClient(Class<K> klass,
			final T target) {
		final Class<? extends Contract<K>> contractClass =
			findContractClass(klass, "ContractForClient");
		return createChecker(klass, target, contractClass, true);
	}

	private static <T> T createChecker(Class<T> klass, final T target,
			final Class<? extends Contract<T>> contractClass, boolean forClient) {
		final Contract<T> contract =
			createContractChecker(contractClass, target);

		@SuppressWarnings("unchecked")
		final T proxy =
			(T) Proxy.newProxyInstance(
				klass.getClassLoader(),
				new Class<?>[] { klass },
				forClient ? createInvocationHandlerForClient(
					target,
					contractClass,
					contract) : createInvocationHandlerForImplementation(
					target,
					contractClass,
					contract));
		return proxy;
	}

	private static <T> InvocationHandler createInvocationHandlerForImplementation(
			final T target, final Class<? extends Contract<T>> contractClass,
			final Contract<T> contract) {
		return new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args)
					throws Throwable {
				final Object result = method.invoke(target, args);
				if (contract != null) {
					final Method checker =
						getCheckMethod(contractClass, method);
					if (checker != null) {
						invokeChecker(contract, checker, args, result);
					}
				}
				return result;
			}

			private <X> void invokeChecker(final Contract<X> contract,
					final Method checker, Object[] args, final Object result)
					throws IllegalAccessException, Throwable,
					InvocationTargetException {
				try {
					checker.invoke(contract, ArrayUtils.addAll(
						new Object[] { result },
						args));
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof ContractError) {
						throw e.getTargetException();
					} else {
						throw e;
					}
				}
			}

			private Method getCheckMethod(
					final Class<? extends Contract<T>> contractClass,
					Method method) {
				try {
					return contractClass.getMethod(
						method.getName(),
						(Class<?>[]) ArrayUtils.addAll(new Class<?>[] { method
							.getReturnType() == Void.TYPE ? Void.class : method
							.getReturnType() }, method.getParameterTypes()));
				} catch (NoSuchMethodException e) {
					return null;
				}
			}
		};
	}

	private static <T> InvocationHandler createInvocationHandlerForClient(
			final T target, final Class<? extends Contract<T>> contractClass,
			final Contract<T> contract) {
		return new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args)
					throws Throwable {
				if (contract != null) {
					final Method checker =
						getCheckMethod(contractClass, method);
					if (checker != null) {
						invokeChecker(contract, checker, args);
					}
				}
				final Object result = method.invoke(target, args);
				return result;
			}

			private <X> void invokeChecker(final Contract<X> contract,
					final Method checker, Object[] args)
					throws IllegalAccessException, Throwable,
					InvocationTargetException {
				try {
					checker.invoke(contract, args);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof ContractError) {
						throw e.getTargetException();
					} else {
						throw e;
					}
				}
			}

			private Method getCheckMethod(
					final Class<? extends Contract<T>> contractClass,
					Method method) {
				try {
					return contractClass.getMethod(method.getName(), method
						.getParameterTypes());
				} catch (NoSuchMethodException e) {
					return null;
				}
			}
		};
	}

	private static <K> Contract<K> createContractChecker(
			final Class<? extends Contract<K>> contractClass, K target) {
		try {
			final Contract<K> contract = contractClass.newInstance();
			contract.target = target;
			return contract;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Class<? extends Contract<T>> findContractClass(
			Class<T> target, String contractClassName) {
		for (Class<?> c : target.getDeclaredClasses()) {
			if (c.getSimpleName().equals(contractClassName)) {
				if (!Contract.class.isAssignableFrom(c))
					throw new IllegalArgumentException("Class "
						+ contractClassName
						+ " (declared at "
						+ target.getName()
						+ ")"
						+ " is not subtype of Contract<T>");
				return (Class<? extends Contract<T>>) c;
			}
		}
		throw new IllegalArgumentException("Class "
			+ contractClassName
			+ " is not defined in "
			+ target.getName());
	}
}
