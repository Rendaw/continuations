# coroutines

Coroutines are methods that can be stopped at any place within and then resumed from that point.  They are often used as lightweight threads, switching between activities in a single thread when an activity needs to wait for something like disk reads or network data to arrive.

This uses org.ow2.asm 5.2 for instrumentation which currently supports up to Java 8 bytecode.  Java 9 class files, even when generated with a target JVM of 1.8, cannot be opened and instrumentation will fail.

Also note that currently calls via method references (like `MyClass::method`) and lambdas may cause issues.

Source mapping isn't affected by coroutine instrumentation so debugging and trace line numbers should operate as normal.

Coroutines are serializable.

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
    new Coroutine(new CoroutineProto() {
        @Override
        public void coExecute() throws SuspendExecution {
            for (int i = 0; i < 3; ++i) {
                System.out.format("hi %s\n", i);
                sleep(worker, 1, TimeUnit.SECONDS);
            }
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

# Usage

Classes and methods that can be suspended need to be instrumented.  This can either be done with a Java agent at runtime, as each class is loaded, or at compile time with an instrumentation step.

Compile time instrumentation is simpler since it has fewer moving parts and you can troubleshoot any assembly issues before distribution.

Runtime instrumentation is more flexible - it can deal with code hotswaps and non-Maven build environments (like IDE incremental compilation), but you need to pass the agent as a JVM argument whenever you execute the code.

## Compile time instrumentation

#### In a single module project

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
    <artifactId>maven-antrun-plugin</artifactId>
    <executions>
        <execution>
            <id>coroutines-instrument-classes</id>
            <phase>compile</phase>
            <configuration>
                <tasks>
                    <taskdef name="instrumentationTask"
                             classname="com.zarbosoft.coroutinescore.instrument.InstrumentationTask"
                             classpath="${maven.dependency.classpath}"/>
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

#### In a multi-module project

To determine the coroutine inheritance and call hierarchy instrumentation must be done in one step (you can't instrument each module separately).  The following shows how to do this by unpacking dependencies that need to be instrumented (or all of them, to make things simpler) before the instrumentation step.

If you don't want to combine all dependencies into your final jar, you can exclude/include classes to unpack in `maven-dependency-plugin`.

Add the following to your `pom.xml`:

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
            <artifactId>coroutines</artifactId>
            <version>1.0.0</version>
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
                             classpath="${maven.dependency.classpath}"/>
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

## Runtime instrumentation

There are three requirements for runtime instrumentation:
1. `com.zarbosoft.coroutinescore.instrument.JavaAgent` is in your classpath, probably by adding it as a dependency
2. You have a jar with a `META-INF/MANIFEST.MF` file with the line `Premain-Class: com.zarbosoft.coroutinescore.instrument.JavaAgent`
3. You start the JVM with `java -javaagent:/requirement/2/jar.jar -jar target/yourapp.jar`

Since 1 and 2 are handled by the Coroutines jar, all you have to do is add the `-javaagent` argument and specify the same jar in both locations for 3.

To run tests in Maven with the agent you can use

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
        <argLine>-javaagent:${com.zarbosoft:coroutines:jar}</argLine>
    </configuration>
</plugin>
```

You can use something similar with the `maven-exec-plugin` if you use that for running your jar normally.  You may have to add the `-javaagent` to your run configuration separately if you're using an IDE.

#### Note:

It's possible to install the agent into the classloader programmatically but I haven't tried it myself.  In this case you need to be careful that no classes to be instrumented are loaded until after installing the agent.

# How it works

1. During suspendable method execution, every stack change (local created, local de-scoped) is mirrored in a thread-local coroutine `Stack` class.
2. `Coroutine.yield()` records the position (instruction index) then raises `SuspendException`.  The stack unwinds normally back to the method that called `coroutine.run`, where normal flow continues.  The stack before yielding is still stored in the coroutine's `Stack`.
3. Instrumentation adds a jump table to each suspendable call to each suspendable method.
4. Resuming the coroutine calls the root function again.  Each function in the previous is re-entered via the jump table at the start of the caller function, and the final jump table moves the instruction pointer to directly after the `yield` call.

# History

This is fairly barebones, and I stripped out some classes (Coiterator) to make it even moreso.  I hope that more fully-featured toolkits and integrations with libraries such as Xnio can use this as a base, and if a better implementation comes out by keeping this small it will be easy to replace.

2017-: rendaw https://github.com/rendaw/coroutines

2013-2017: https://github.com/buzden/continuations

Copyright (c) 2008-2013, Matthias Mann
* LICENSE: New BSD (http://directory.fsf.org/wiki/License:BSD_3Clause)
* HOMEPAGE: http://www.matthiasmann.de/content/view/24/26/
