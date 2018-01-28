package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class BasicsTest {

	@Test
	public void testEmptyCoroutine() {
		final Coroutine coroutine = new Coroutine(() -> {

		});
		coroutine.run();
		assertThat(coroutine.getState(), equalTo(FINISHED));
	}

	@Test
	public void testSuspend1() {
		final Coroutine coroutine = new Coroutine(() -> {
			Coroutine.yield();
		});
		coroutine.run();
		coroutine.run();
		assertThat(coroutine.getState(), equalTo(FINISHED));
	}

	@Test
	public void testSuspend2() {
		final Coroutine coroutine = new Coroutine(() -> {
			Coroutine.yield();
			Coroutine.yield();
		});
		coroutine.run();
		coroutine.run();
		coroutine.run();
		assertThat(coroutine.getState(), equalTo(FINISHED));
	}

	@Test
	public void testSuspend1000() {
		final Coroutine coroutine = new Coroutine(() -> {
			for (int i = 0; i < 1000; ++i) {
				Coroutine.yield();
			}
		});
		for (int i = 0; i < 1001; ++i) {
			coroutine.run();
		}
		assertThat(coroutine.getState(), equalTo(FINISHED));
	}

	@Test
	public void testUnwindBlocks() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			{
				b.append("1");
				Coroutine.yield();
				b.append("2");
			}
			b.append("3");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		co.run();
		assertThat(b.toString(), equalTo("0123"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	public static void testUnwindCallsMethod(final StringBuilder b) throws SuspendExecution {
		b.append("1");
		Coroutine.yield();
		b.append("2");
	}

	@Test
	public void testUnwindCalls() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			testUnwindCallsMethod(b);
			b.append("3");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		co.run();
		assertThat(b.toString(), equalTo("0123"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

}
