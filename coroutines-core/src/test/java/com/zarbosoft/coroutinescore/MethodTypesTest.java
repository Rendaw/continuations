package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class MethodTypesTest {

	public static void testStaticInner(final StringBuilder b) throws SuspendExecution {
		b.append("a");
		Coroutine.yield();
		b.append("b");
	}

	@Test
	public void testStatic() {
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(() -> {
			testStaticInner(b);
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner {
			public void method() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner().method();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testInheritedMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner1 {
			public void method() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		}
		class Inner2 extends Inner1 {
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner2().method();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testInheritedStaticMethod() {
		class Inner extends MethodTypesTest {

		}
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(() -> {
			Inner.testStaticInner(b);
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	public static abstract class TestAbstract {
		public abstract void method() throws SuspendExecution;
	}

	@Test
	public void testAbstractMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner extends TestAbstract {

			@Override
			public void method() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner().method();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	public interface TestInterface {
		void method() throws SuspendExecution;

		default void method1(final StringBuilder b) throws SuspendExecution {
			b.append("a");
			Coroutine.yield();
			b.append("b");
		}
	}

	@Test
	public void testInterfaceMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner implements TestInterface {

			@Override
			public void method() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner().method();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testDefaultMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner implements TestInterface {
			@Override
			public void method() throws SuspendExecution {
			}
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner().method1(b);
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testSuperMethod() {
		final StringBuilder b = new StringBuilder();
		class Inner1 {
			public void method() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		}
		class Inner2 extends Inner1 {
			@Override
			public void method() throws SuspendExecution {
				b.append("l");
				super.method();
				b.append("m");
			}
		}
		final Coroutine coroutine = new Coroutine(() -> {
			new Inner2().method();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("labm"));
	}

	@Test
	public void testAnonymous() {
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(() -> {
			new SuspendableRunnable() {
				@Override
				public void run() throws SuspendExecution {
					b.append("a");
					Coroutine.yield();
					b.append("b");
				}
			}.run();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testLambda() {
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(() -> {
			final SuspendableRunnable r = () -> {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			};
			r.run();
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testRootLambda() {
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(() -> {
			b.append("a");
			Coroutine.yield();
			b.append("b");
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	@Test
	public void testRootAnonymous() {
		final StringBuilder b = new StringBuilder();
		final Coroutine coroutine = new Coroutine(new SuspendableRunnable() {
			@Override
			public void run() throws SuspendExecution {
				b.append("a");
				Coroutine.yield();
				b.append("b");
			}
		});
		coroutine.run();
		coroutine.run();
		assertThat(b.toString(), equalTo("ab"));
	}

	public void testRefMethod(final StringBuilder b) throws SuspendExecution {
		b.append("y");
		Coroutine.yield();
		b.append("z");
	}

	@FunctionalInterface
	interface SuspendableConsumer {
		void x(StringBuilder b) throws SuspendExecution;
	}

	@Test
	public void testReference() {
		final StringBuilder b = new StringBuilder();
		final SuspendableConsumer inner = this::testRefMethod;
		final Coroutine coroutine = new Coroutine(() -> {
			b.append("a");
			inner.x(b);
			b.append("b");
		});
		coroutine.run();
		assertThat(b.toString(), equalTo("ay"));
		coroutine.run();
		assertThat(b.toString(), equalTo("ayzb"));
	}

	public static void testRefStaticMethod(final StringBuilder b) throws SuspendExecution {
		b.append("y");
		Coroutine.yield();
		b.append("z");
	}

	@Test
	public void testStaticReference() {
		final StringBuilder b = new StringBuilder();
		final SuspendableConsumer inner = MethodTypesTest::testRefStaticMethod;
		final Coroutine coroutine = new Coroutine(() -> {
			b.append("a");
			inner.x(b);
			b.append("b");
		});
		coroutine.run();
		assertThat(b.toString(), equalTo("ay"));
		coroutine.run();
		assertThat(b.toString(), equalTo("ayzb"));
	}
}
