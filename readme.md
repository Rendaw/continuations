# coroutines

Coroutines are methods that can be stopped at any place within and then resumed from that point.  They are often used as lightweight threads, switching between activities in a single thread when an activity needs to wait for something like disk reads or network data to arrive.

Now supports Java 9!

Source mapping isn't affected by coroutine instrumentation so debugging and trace line numbers should operate as normal.

Coroutines are serializable.

This package is barebones.  I hope that more fully-featured toolkits and integrations with libraries such as Xnio can use this as a base, and if a better implementation comes out by keeping this small it will be easy to replace.  My recommendation is [com.zarbosoft.coroutines](https://github.com/rendaw/java-coroutines).

# Maven

```
<dependency>
    <groupId>com.zarbosoft</groupId>
    <artifactId>coroutines-core</artifactId>
    <version>0.0.9</version>
</dependency>
```

# Example

```
public static void sleep(
        final XnioWorker worker, final int time, final TimeUnit unit
) throws SuspendExecution {
    final Coroutine self = Coroutine.getActiveCoroutine();
    worker.getIoThread().executeAfter(new Runnable() {
        @Override
        public void run() {
            self.run();
        }
    }, time, unit);
    Coroutine.yield();
}

public static void main(final String[] args) throws Exception {
    final Xnio xnio = Xnio.getInstance();
    final XnioWorker worker = xnio.createWorker(OptionMap
            .builder()
            .set(Options.WORKER_TASK_MAX_THREADS, 1)
            .set(Options.WORKER_IO_THREADS, 1)
            .getMap());
    new Coroutine(() -> {
        for (int i = 0; i < 3; ++i) {
            System.out.format("hi %s\n", i);
            sleep(worker, 1, TimeUnit.SECONDS);
        }
    }).run();
    worker.awaitTermination();
}
```

prints

```
hi 0
hi 1
hi 2
```

# Programming with coroutines

Make suspendable methods by adding `throws SuspendExecution` to the signature.  Suspendable methods can be called from other suspendable methods.  Don't catch `SuspendExecution` explicitly (catching a less specific exception class such as `Exception` or `Throwable` is fine).

Start a coroutine by creating a `Coroutine` with a suspendable method as a starting point and call `run` to start it and block until it suspends.  Suspended coroutines can be restarted with `run`.

# Running your code

Classes and methods that can be suspended need to be instrumented.  This can either be done with a Java agent at runtime, as each class is loaded, or at compile time with an instrumentation step.

**Compile time** instrumentation is simpler since it has fewer moving parts and you can troubleshoot any assembly issues before distribution.  It also may improve startup times slightly.

**Runtime instrumentation** is more flexible - it can deal with code hotswaps and build environments that can't use the Ant instrumentation task, but you need to pass the agent as a JVM argument whenever you execute the code.

Compile and runtime instrumentation can be mixed, so you can use preinstrumented classes with the runtime agent.

I suggest compile-time instrumenting libraries you are distributing since it makes downstream compile time instrumentation simpler.

## Compile-time instrumentation

Compile-time instrumentation uses an Ant Task to modify the class files. Add the following to your `pom.xml`:

```
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>2.3</version>
    <executions>
        <execution>
            <id>getClasspathFilenames</id>
            <goals>
                <goal>properties</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-antrun-plugin</artifactId>
    <executions>
        <execution>
            <id>coroutines-instrument</id>
            <phase>compile</phase>
            <configuration>
                <tasks>
                    <taskdef name="instrumentationTask"
                             classname="com.zarbosoft.coroutinescore.instrument.InstrumentationTask"
                             classpathref="maven.dependency.classpath"/>
                    <instrumentationTask>
                        <fileset dir="${project.build.directory}/classes/" includes="**/*.class"/>
                    </instrumentationTask>
                </tasks>
            </configuration>
            <goals>
                <goal>run</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### Verbose output

Change `<instrumentationTask>` to `<instrumentationTask verbose="true">`.

#### Bytecode verification

Change `<instrumentationTask>` to `<instrumentationTask check="true">`.

#### Instrumenting test classes

If you want to instrument your test classes as well, copy the `coroutines-instrument` execution as a second execution with id `coroutines-instrument-tests` (or something else of your choice).

Change the `fileset` to

```
<fileset dir="${project.build.directory}/test-classes/" includes="**/*.class"/>
```

and `phase` to `test-compile`.

#### Dealing with uninstrumented suspendable dependencies

Hopefully this shouldn't happen often.  If you don't care about compile time instrumentation, using runtime instrumentation will work fine.  Otherwise, there's no simple way to do this but the following works alright.  Basically:

* Unpack the dependencies that need to be instrumented (or all of them) into your classes directory
* Instrument everything
* Create a semi-uber jar with the instrumented classes, excluding those dependencies

Since it's easiest to unpack and instrument everything, I'll demonstrate that. Add the following to your `pom.xml`:

```
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.0.2</version>
    <executions>
        <execution>
            <id>getClasspathFilenames</id>
            <goals>
                <goal>properties</goal>
            </goals>
        </execution>
        <execution>
            <id>unpack-dependencies</id>
            <phase>compile</phase>
            <goals>
                <goal>unpack-dependencies</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.build.directory}/classes</outputDirectory>
                <includeScope>runtime</includeScope>
		<!--
			Signatures broke my jar - got the unhelpful error
			"Could not find or load main class ..."
		-->
		<excludes>META-INF/*.DSA,META-INF/*.SF</excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-antrun-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.zarbosoft</groupId>
            <artifactId>coroutines-core</artifactId>
            <version>0.0.9</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>coroutines-instrument-classes</id>
            <phase>compile</phase>
            <configuration>
                <tasks>
                    <taskdef name="instrumentationTask"
                             classname="com.zarbosoft.coroutinescore.instrument.InstrumentationTask"
                             classpathref="maven.dependency.classpath"/>
                    <instrumentationTask>
                        <fileset dir="${project.build.directory}/classes/" includes="**/*.class"/>
                    </instrumentationTask>
                </tasks>
            </configuration>
            <goals>
                <goal>run</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Then the default maven-jar-plugin execution will combine everything into a single jar.

## Runtime instrumentation

There are three requirements for runtime instrumentation:
1. `com.zarbosoft.coroutinescore.instrument.JavaAgent` is in your classpath, probably by adding it as a dependency
2. You have a jar with a `META-INF/MANIFEST.MF` file with the line `Premain-Class: com.zarbosoft.coroutinescore.instrument.JavaAgent`
3. You start the JVM with the flag `-javaagent:/requirement/2/file.jar`

If the coroutines-core jar is in your classpath 1 and 2 are complete and all you have to do is add the `-javaagent` argument and specify the same jar in both locations for 3.

For example, with maven:

```
java -javaagent:/home/you/.m2/repository/com/zarbosoft/coroutines-core/coroutines-core-0.0.9.jar -jar myjar.jar
```

#### Verbose output

Add the option `=v` after the jar:

```
java -javaagent:/home/you/.m2/repository/com/zarbosoft/coroutines-core/coroutines-core-0.0.9.jar=v -jar myjar.jar
```

#### Bytecode verification

Add the option `=c` after the jar:

```
java -javaagent:/home/you/.m2/repository/com/zarbosoft/coroutines-core/coroutines-core-0.0.9.jar=c -jar myjar.jar
```

Flags can be combined, like `=cv` for verbose output and bytecode verification.

#### Instrumenting test classes

Add the following to your `pom.xml`:

```
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>2.3</version>
    <executions>
        <execution>
            <id>getClasspathFilenames</id>
            <goals>
                <goal>properties</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.20.1</version>
    <configuration>
        <argLine>-javaagent:${com.zarbosoft:coroutines-core:jar}</argLine>
    </configuration>
</plugin>
```

You can use something similar with the `maven-exec-plugin` if you use that for running your jar normally.  You may have to add the `-javaagent` to your run configuration separately if you're using an IDE.

#### Note:

It's also possible to install the agent into the classloader programmatically but I haven't tried it myself.  In this case you need to be careful that no classes to be instrumented are loaded until after installing the agent.

# How it works

1. Before every suspendable call, the coroutine state (local variables, stack variables) are saved to a thread-local `Stack` object.
2. `Coroutine.yield()` records the position (instruction index) then raises `SuspendException`.  The stack unwinds normally back to the method that called `coroutine.run`, where normal flow continues.  The stack before yielding is still stored in the coroutine's `Stack`.
3. Instrumentation adds a jump table to each suspendable call to each suspendable method.
4. Resuming the coroutine calls the root function again.  Each function restores the latest state from the `Stack` and jumps to the point it suspended.  If a method call was suspended, the method is re-entered and the process repeats.  The final method jumps to directly after the `yield` call.

# History

This is fairly barebones, and I stripped out some classes (Coiterator) to make it even moreso.  I hope that more fully-featured toolkits and integrations with libraries such as Xnio can use this as a base, and if a better implementation comes out by keeping this small it will be easy to replace.  My own wrapper is [com.zarbosoft.coroutines](https://github.com/rendaw/java-coroutines).

2017-: rendaw https://github.com/rendaw/java-coroutines-core

2013-2017: https://github.com/buzden/continuations

Copyright (c) 2008-2013, Matthias Mann
* LICENSE: New BSD (http://directory.fsf.org/wiki/License:BSD_3Clause)
* HOMEPAGE: http://www.matthiasmann.de/content/view/24/26/
