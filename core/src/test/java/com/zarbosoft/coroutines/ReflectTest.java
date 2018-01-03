package com.zarbosoft.coroutines;

import org.junit.Test;

import java.lang.reflect.Method;

import static com.zarbosoft.coroutines.Coroutine.State.FINISHED;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class ReflectTest {

	public void method() throws SuspendExecution {
		Coroutine.yield();
	}

	@Test
	public void testSuspend() throws NoSuchMethodException {
		final Coroutine co = new Coroutine(new CoroutineProto() {
			Method method = ReflectTest.class.getMethod("method");

			public final void run() throws SuspendExecution {
				try {
					method.invoke(ReflectTest.this);
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		co.run();
		co.run();
		assertThat(co.getState(), equalTo(FINISHED));
	}

}