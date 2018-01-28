package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ExceptionTest {

	public class MyException1 extends RuntimeException {
	}

	public class MyException2 extends RuntimeException {
	}

	@Test
	public void testCatchThrowable() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
			} catch (final Throwable e) {
				b.append("3");
			}
			b.append("4");
		});
		co.run();
		co.run();
		assertThat(b.toString(), equalTo("0124"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testCatchError() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
			} catch (final Error e) {
				b.append("3");
			}
			b.append("4");
		});
		co.run();
		co.run();
		assertThat(b.toString(), equalTo("0124"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testCatchException() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
			} catch (final Exception e) {
				b.append("3");
			}
			b.append("4");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		co.run();
		assertThat(b.toString(), equalTo("0124"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testCatchThenYield() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				if (true)
					throw new MyException1();
				b.append("2");
			} catch (final Exception e) {
				b.append("3");
			}
			b.append("4");
			Coroutine.yield();
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("0134"));
		co.run();
		assertThat(b.toString(), equalTo("01345"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldThenCatch() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			Coroutine.yield();
			b.append("1");
			try {
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} catch (final Exception e) {
				b.append("4");
			}
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("0"));
		co.run();
		assertThat(b.toString(), equalTo("01245"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldCaught() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} catch (final MyException1 e) {
				b.append("4");
			}
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		co.run();
		assertThat(b.toString(), equalTo("01245"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldUncaught() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} catch (final MyException2 e) {
				b.append("4");
			}
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		try {
			co.run();
		} catch (final MyException1 e) {

		}
		assertThat(b.toString(), equalTo("012"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldTryNoCatchBlock() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
			} finally {
				b.append("3");
			}
			b.append("4");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		co.run();
		assertThat(b.toString(), equalTo("01234"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldTryNoCatchBlockExcept() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} finally {
				b.append("4");
			}
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		try {
			co.run();
		} catch (final MyException1 e) {

		}
		assertThat(b.toString(), equalTo("0124"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldTryCatchBlockCaught() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} catch (final MyException1 e) {
				b.append("4");
			} finally {
				b.append("5");
			}
			b.append("6");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		try {
			co.run();
		} catch (final MyException1 e) {

		}
		assertThat(b.toString(), equalTo("012456"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldTryCatchBlockUncaught() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				Coroutine.yield();
				b.append("2");
				if (true)
					throw new MyException1();
				b.append("3");
			} catch (final MyException2 e) {
				b.append("4");
			} finally {
				b.append("5");
			}
			b.append("6");
		});
		co.run();
		assertThat(b.toString(), equalTo("01"));
		try {
			co.run();
		} catch (final MyException1 e) {

		}
		assertThat(b.toString(), equalTo("0125"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldCatchBlock() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				if (true)
					throw new MyException1();
				b.append("2");
			} catch (final MyException1 e) {
				b.append("3");
				Coroutine.yield();
				b.append("4");
			} finally {
				b.append("5");
			}
		});
		co.run();
		assertThat(b.toString(), equalTo("013"));
		co.run();
		assertThat(b.toString(), equalTo("01345"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldCatchBlockUncaught() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
				if (true)
					throw new MyException1();
				b.append("2");
			} catch (final MyException1 e) {
				b.append("3");
				Coroutine.yield();
				b.append("4");
				if (true)
					throw new MyException1();
				b.append("5");
			} finally {
				b.append("6");
			}
			b.append("7");
		});
		co.run();
		assertThat(b.toString(), equalTo("013"));
		try {
			co.run();
		} catch (final MyException1 e) {

		}
		assertThat(b.toString(), equalTo("01346"));
		assertThat(co.getState(), equalTo(FINISHED));
	}

	@Test
	public void testYieldFinally() {
		final StringBuilder b = new StringBuilder();
		final Coroutine co = new Coroutine(() -> {
			b.append("0");
			try {
				b.append("1");
			} catch (final MyException1 e) {
				b.append("2");
			} finally {
				b.append("3");
				Coroutine.yield();
				b.append("4");
			}
			b.append("5");
		});
		co.run();
		assertThat(b.toString(), equalTo("013"));
		co.run();
		assertThat(b.toString(), equalTo("01345"));
		assertThat(co.getState(), equalTo(FINISHED));
	}
}
