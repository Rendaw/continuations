package com.zarbosoft.coroutinescore;

import org.hamcrest.number.IsCloseTo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class StackTest {
	@Test
	public void testInteger() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final int x = 7;
			Coroutine.yield();
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testDouble() {
		final double[] out = new double[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final double x = 7;
			Coroutine.yield();
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], IsCloseTo.closeTo(7d, 0.00001));
	}

	@Test
	public void testNull() {
		final Object[] out = new Object[] {this};
		final Coroutine coroutine = new Coroutine(() -> {
			final Object x = null;
			Coroutine.yield();
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		assertNull(out[0]);
	}

	@Test
	public void testReference() {
		final Object[] out = new Object[] {null};
		final Object y = new Object();
		final Coroutine coroutine = new Coroutine(() -> {
			final Object x = y;
			Coroutine.yield();
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(y));
	}

	@Test
	public void testUninitialized1() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final int x;
			Coroutine.yield();
			x = 7;
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testUninitialized2() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final int x;
			Coroutine.yield();
			x = 7;
			Coroutine.yield();
			out[0] = x;
		});
		coroutine.run();
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testBlock() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			{
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testIf() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			if (true) {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testFor() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			for (int i = 0; i < 2; ++i) {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		assertThat(out[0], equalTo(-1));
		coroutine.run();
		assertThat(out[0], equalTo(7));
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testTry() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			try {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			} finally {
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	private static class MyException extends RuntimeException {

	}

	@Test
	public void testCatch() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			try {
				throw new MyException();
			} catch (final MyException e) {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testFinally() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			try {
			} finally {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testFinallyCaught() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			try {
				throw new MyException();
			} catch (final MyException e) {
			} finally {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testFinallyUncaught() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			try {
				throw new MyException();
			} finally {
				final int x = 7;
				Coroutine.yield();
				out[0] = x;
			}
		});
		coroutine.run();
		try {
			coroutine.run();
		} catch (final MyException e) {
		}
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testCapturesAnonymous() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final int y = 7;
			new SuspendableRunnable() {
				@Override
				public void run() throws SuspendExecution {
					Coroutine.yield();
					out[0] = y;
				}
			}.run();
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}

	@Test
	public void testCapturesLambda() {
		final int[] out = new int[] {-1};
		final Coroutine coroutine = new Coroutine(() -> {
			final int y = 7;
			final SuspendableRunnable r = () -> {
				Coroutine.yield();
				out[0] = y;
			};
			r.run();
		});
		coroutine.run();
		coroutine.run();
		assertThat(out[0], equalTo(7));
	}
}
