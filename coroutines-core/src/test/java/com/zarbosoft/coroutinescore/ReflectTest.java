package com.zarbosoft.coroutinescore;

import org.junit.Test;

import java.lang.reflect.Method;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class ReflectTest {
	public static Method methodSuspend;
	public static Method methodNoSuspend;

	static {
		try {
			methodSuspend = ReflectTest.class.getMethod("methodSuspend", StringBuilder.class);
			methodNoSuspend = ReflectTest.class.getMethod("methodNoSuspend", StringBuilder.class);
		} catch (final NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public void methodSuspend(final StringBuilder b) throws SuspendExecution {
		b.append("b");
		Coroutine.yield();
		b.append("c");
	}

	public void methodNoSuspend(final StringBuilder b) throws SuspendExecution {
		b.append("b");
	}

	@Test
	public void testSuspend() throws NoSuchMethodException {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			try {
				b.append("a");
				methodSuspend.invoke(ReflectTest.this, b);
				b.append("d");
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		});
		co.run();
		assertThat(b.toString(), equalTo("ab"));
		co.run();
		assertThat(b.toString(), equalTo("abcd"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testNoSuspend() throws NoSuchMethodException {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			try {
				b.append("a");
				methodNoSuspend.invoke(ReflectTest.this, b);
				b.append("c");
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		});
		co.run();
		assertThat(b.toString(), equalTo("abc"));
		assertThat(co.getState(), equalTo(FINISHED));
	}
}