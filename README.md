# coroutines

Coroutines are methods that can be stopped at any place within and then resumed from that point.

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
            for (int i = 0; i < 10; ++i) {
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

Classes and methods that can be suspended need to be modified to do the suspending.  This can either be done with
a Java agent at runtime, as each class is loaded, or at compile time with an instrumentation step.

Compile time instrumentation is simpler since it has fewer moving parts and you can troubleshoot any assembly issues
before distribution.

Runtime instrumentation is more flexible - it can deal with code hotswaps and non-Maven build environments (like
IDE incremental compilation), but you need to pass the agent as a JVM argument whenever you execute the code.

## Compile time instrumentation

In Maven, just add the following:

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
                             classname="com.zarbosoft.coroutines.instrument.InstrumentationTask"
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
1. `com.zarbosoft.coroutines.instrument.JavaAgent` is in your classpath, probably by adding it as a dependency
2. You have a jar with a `META-INF/MANIFEST.MF` file with the line `Premain-Class: com.zarbosoft.coroutines.instrument.JavaAgent`
3. You start the JVM with `java -javaagent:/requirement/2/jar.jar -jar target/yourapp.jar`

Since 1 and 2 are handled by the Coroutines jar, all you have to do is add the `-javaagent` argument.

Run your jar with:
```
java -javaagent:/path/to/coroutines-1.0.0.jar -jar target/yourapp.jar
```

In your IDE this may need to be the full path to the jar in your `~/.m2` directory.  In Maven you can use

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

to use the agent when running tests, and something similar with the `maven-exec-plugin` if you use that for running your
 jar.

#### Note:

It's possible to add the JavaAgent programmatically but I haven't tried it myself.  In this case you need to be careful
that no classes to be instrumented are loaded until after installing the agent.

# History
2017-: rendaw https://github.com/rendaw/coroutines
2013-2017: https://github.com/buzden/continuations
Copyright (c) 2008-2013, Matthias Mann
* LICENSE: New BSD (http://directory.fsf.org/wiki/License:BSD_3Clause)
* HOMEPAGE: http://www.matthiasmann.de/content/view/24/26/
