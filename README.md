# Shakespeare plays Reactive Scrabble with Queues

JMH benchmark of several queue libraries and their ability to coordinate threads,
for several common use-cases (`-p mode=`):

 * throughput (fast): many small tasks, no latency, no back pressure
 * heavy lifting (all): a few (eg, 100-200) larger tasks, no latency, no back pressure
 * waiting (burn): efficiency of a few small tasks with latency, no back pressure
 * mixed load (cost): many small tasks and a few large ones interspersed, no latency, no back pressure
 * back pressure (delay, effort): many small tasks, with back pressure which may induce latency

## Results





#### Reactive to What ?

 * task size: small, large, mixed
 * back pressure: eg, because tasks use a lot of memory
 * latency: eg, tasks coming from the network
 * efficiency under low load: waiting for tasks shouldn't burn the cpu.
This is measured by timing a cpu-intensive task running in the background.


#### Queues

* [Conversant Disruptor](https://github.com/conversant/disruptor): a fast blocking queue
* Java 8 streams
* the Java ForkJoinPool
* [JcTools](https://github.com/JCTools/JCTools): psy-lob-saw's concurrent data structures
* [Kilim](https://github.com/kilim/kilim): fibers, continuations and message passing for java
* [Quasar](https://github.com/puniverse/quasar): fibers and message passing for java and kotlin
* [RxJava](https://github.com/ReactiveX/RxJava): reactive extensions for the JVM

Caveat Emptor - the author is a Kilim user and maintainer.

#### JMH Params

the following JMH `Params` define the scenarios:

 * mode:
modes allow setting multiple params as a group and take precedence over those same params.
only the first letter is needed.
supported values are: fast, all, burn, cost, delay, effort.
some single letter modes can embed other param values as a suffix.
ie "a200" does 200 iterations and d80 or e80 uses a soft limit of 80.
 * suffix: for words matching the suffix, hash the word and modify the score
 * numHash: the number of times to hash each word matching the suffix
 * size: if non-zero, the nominal queue size. not honored by all implementations
 * soft: the soft limit on the number of outstanding words, ie simulated memory pressure.
only active for positive 'sleep' values
 * sleep: for positive values, the number of times to sleep before exceeding the soft limit.
if less than -1, only iterate through the first -sleep values.
if -1, burn the cpu using an additional task and only use the first 100 values.


Some jvm `-D` flags are accepted:
* `-Dfast`: only store the 3 best scores at any time
* `-Dnp=4`: number of cpus to assume. default is number of available cpus
* when run from `main`, as opposed to from JMH, any of the JMH `Params` can be set


these params and flags can be useful for understanding how the implementations perform.
In addition to the soft limit, when active there is also a hard limit that will result in
`System.exit(0)` (missing results mean zero score).


## Running

```
mvn package
java -jar target/benchmarks.jar
```

#### Thermal Throttling

In some environments, the OS or cpu will perform thermal throttling.
For very long runs of a single implementation, this is ok.
But to get accurate results from shorter runs of many implementations,
it helps to throttle the cpu to a speed that is sustainable.
eg, on linux:

```
sudo cpupower frequency-set -u 2.8ghz
```


#### Quasar

Quasar uses a java agent.
This agent appears to result in decreased performance for the other implementations.
eg, for the RxJava throughput scenario:

```
Benchmark                       Cnt    Score   Error  Units
RxJava.bench with quasar agent   36  192.157 ± 3.961  ops/s
RxJava.bench     without agent   36  210.546 ± 2.022  ops/s
```

As a result, it's better to run the other implementations as above without the agent
and run the Quasar implementations separately.
Quasar also requires different dependencies for Java 8 and earlier than for Java 9 and later.
The `master` branch supports Java 9 and later.
Checkout the `quasar7` tag for Java 8 and earlier.
To run the Quasar implementations alone:

```
java -version 2>&1 | head -n1 | grep -q "version\W*1.8" && git checkout quasar7
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
quasar="-javaagent:$(echo $cp | grep -o '[^:]*' | grep quasar-core)"
java $quasar -jar target/benchmarks.jar -- Quasar
```

#### Verification

To run all the implementations without JMH and view the results:

```
java $quasar -cp target/classes:$cp direct.ShakespearePlaysScrabbleWithQueues
```

any of the JMH `Params` can be set by setting the same system property,
eg `-Dmode=cost` is equivalent to `-p mode=cost`.
`Stream8` typically fails the hard-limit for scenarios with back pressure,
which results in an immediate (intentional) exit.



## Meta

#### License

Apache 2.0 License

#### History

This project is based on a merged fork of two projects

* [Jose Paumard's kata from Devoxx 2015](https://github.com/JosePaumard/jdk8-stream-rx-comparison-reloaded)
* [David Karnok's (RxJava) direct implementation](https://github.com/akarnokd/akarnokd-misc/blob/master/src/jmh)

This project adds several multi-threaded implementations using different queues
for communicating between the threads and additional iterators for testing different scenarios.

This bench uses two data files: `ospd.txt` and `words.shakespeare.txt`.
They can be freely downloaded from Robert Sedgewick page here: http://introcs.cs.princeton.edu/java/data/.
Those two data sets files are under the copyright of their authors, and provided for convenience only.

#### Contributions

Pull requests are encouraged for new use cases, new benchmark implementations,
and optimization of existing implementations.
Also interested in results from other environments.
Some cleanup is warranted before adding a new use case, so creating an issue
first would be beneficial.


#### Todo

* clean up the scenario selection / configuration code
