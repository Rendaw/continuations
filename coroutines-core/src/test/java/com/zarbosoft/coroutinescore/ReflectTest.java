package com.zarbosoft.coroutinescore;

import org.junit.Test;

import java.lang.reflect.Method;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class ReflectTest {
	public static Method method;

	static {
		try {
			method = ReflectTest.class.getMethod("method");
		} catch (final NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public void method() throws SuspendExecution {
		Coroutine.yield();
	}

	@Test
	public void testSuspend() throws NoSuchMethodException {
		final Coroutine co = new Coroutine(() -> {
			try {
				method.invoke(ReflectTest.this);
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		});
		co.run();
		co.run();
		assertThat(co.getState(), equalTo(FINISHED));
	}

}