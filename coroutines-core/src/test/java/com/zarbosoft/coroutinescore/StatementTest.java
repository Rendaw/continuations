package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class StatementTest {
  public static class MyClass1 {
    int x = 3;
  }

  public static int method1() throws SuspendExecution {
    Coroutine.yield();
    return 71;
  }

  public static MyClass1 method2() throws SuspendExecution {
    Coroutine.yield();
    return new MyClass1();
  }

  @Test
  public void testSimpleCall() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              Coroutine.yield();
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("ab"));
  }

  @Test
  public void testAssignment() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              final int x = method1();
              b.append(Integer.toString(x));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a71b"));
  }

  @Test
  public void testLHS() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              method2().x = 4;
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("ab"));
  }

  @Test
  public void testSequence() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append(String.format("%s%s%s%s", "a", method1(), method1(), "b"));
            });
    coroutine.run();
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a7171b"));
  }

  @Test
  public void testPreincrement() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              int x = 0;
              b.append("a");
              b.append(Integer.toString(method1() + ++x));
              b.append(Integer.toString(x));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a721b"));
  }

  @Test
  public void testPostincrement() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              int x = 0;
              b.append("a");
              b.append(Integer.toString(x++ + method1()));
              b.append(Integer.toString(x));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a711b"));
  }

  @Test
  public void testAccess() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              b.append(Integer.toString(method2().x));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a3b"));
  }

  @Test
  public void testConstructor() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              b.append(new String(Integer.toString(method1())));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a71b"));
  }

  public static boolean testBooleanMethod1(final StringBuilder b, final String s) {
    b.append(s);
    return true;
  }

  public static boolean testBooleanMethod2(final StringBuilder b, final String s1, final String s2)
      throws SuspendExecution {
    b.append(s1);
    Coroutine.yield();
    b.append(s2);
    return true;
  }

  @Test
  public void testBooleanNoShortCircuitFirst() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              if (!testBooleanMethod2(b, "b", "c") || testBooleanMethod1(b, "d")) {
                b.append("e");
              } else {
                b.append("f");
              }
            });
    coroutine.run();
    assertThat(b.toString(), equalTo("ab"));
    coroutine.run();
    assertThat(b.toString(), equalTo("abcde"));
  }

  @Test
  public void testBooleanNoShortCircuitSecond() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              if (!testBooleanMethod1(b, "b") || testBooleanMethod2(b, "c", "d")) {
                b.append("e");
              } else {
                b.append("f");
              }
            });
    coroutine.run();
    assertThat(b.toString(), equalTo("abc"));
    coroutine.run();
    assertThat(b.toString(), equalTo("abcde"));
  }

  @Test
  public void testBooleanShortCircuitFirst() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              if (testBooleanMethod2(b, "b", "c") || testBooleanMethod1(b, "d")) {
                b.append("e");
              } else {
                b.append("f");
              }
            });
    coroutine.run();
    assertThat(b.toString(), equalTo("ab"));
    coroutine.run();
    assertThat(b.toString(), equalTo("abce"));
  }

  @Test
  public void testBooleanShortCircuitSecond() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              if (testBooleanMethod1(b, "b") || testBooleanMethod2(b, "c", "d")) {
                b.append("e");
              } else {
                b.append("f");
              }
            });
    coroutine.run();
    assertThat(b.toString(), equalTo("abe"));
  }

  public static class MyError extends RuntimeException {
    public MyError(final String s) {
      super(s);
    }
  }

  @Test
  public void testThrow() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              throw new MyError(Integer.toString(method1()));
            });
    coroutine.run();
    try {
      coroutine.run();
    } catch (final MyError e) {
      assertThat(e.getMessage(), equalTo("71"));
    }
    assertThat(b.toString(), equalTo("a"));
  }

  public static int testReturnMethod() throws SuspendExecution {
    return method1();
  }

  @Test
  public void testReturn() {
    final StringBuilder b = new StringBuilder();
    final Coroutine coroutine =
        new Coroutine(
            () -> {
              b.append("a");
              b.append(Integer.toString(testReturnMethod()));
              b.append("b");
            });
    coroutine.run();
    coroutine.run();
    assertThat(b.toString(), equalTo("a71b"));
  }
}
