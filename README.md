# Shakespeare plays Reactive Scrabble with Java Queues

This JMH benchmark attempts to measure how quickly an actor or queue library
can distribute and process tasks on multi-core machines,
for several common use-cases (`-p mode=`):

 * heavy lifting (all): a few (eg, 100-200) larger tasks, no latency, no back pressure
 * waiting (burn): efficiency of a few small tasks with latency, no back pressure
 * mixed load (cost): many small tasks and a few large ones interspersed, no latency, no back pressure
 * back pressure (delay, effort): many small tasks, with back pressure which may induce latency
 * throughput (fast): many small tasks, no latency, no back pressure

Collectively, the use-cases attempt to represent the degree to which an implementation is reactive
to it's environment.

Based on Jose Paumard's kata from Devoxx 2015 and David Karnok's (RxJava) refinements,
the tasks find the most valuable word
from a set of words taken from one of Shakespeare's works based on the  point schema of Scrabble.
Larger tasks are modeled by sha-256 hashing many variations of a word, adding to the word's score.
The candidate words are provided by several iterators exercising the different use-cases.


#### Results

<img src="https://github.com/nqzero/reactive-bench/blob/jmh.data/doc/bench.jpg?raw=true">
<img src="https://github.com/nqzero/reactive-bench/blob/jmh.data/doc/details.jpg?raw=true">

Kilim performs best overall, best for mixed load, and is the most versatile:
it's worst use-case score is 64%, vs 51% for the next-best worst-case.
However, Kilim lacks multi-consumer messaging, so it could degrade to a single thread
under unusual conditions.
(multi-consumer messaging is under development and was one of the motivations for this benchmark study)

JcTools is the best-performing queue for the back pressure use-case,
though none of the implementations handle this use-case gracefully on all machine types.
This is an area of ongoing investigation.

Quasar is the clear winner for heavy lifting, benefitting from both efficient context switching (like Kilim)
and a multi-consumer `Channel`

Java 8 Streams lack the concept of back pressure and effectively read the entire event stream into an array
before processing it in parallel chunks, which unsurprisingly results in the highest throughput.
Kilim is the fastest of the queue implementations, benefitting from fast context switching and
the use of multiple queues.

Conversant is the most consistent performer across the use-cases.
The difference between the use-case mean and the worst use-case is only 15%,
vs 22-62% for the other implementations.

RxJava forces you to chose between parallel processing (which is used here) and handling back pressure,
which appears to make it inappropriate unless you know that you won't be processing
cpu intensive tasks.
Hopefully the RxJava team will remove this limitation in the future.


#### Reactive to What ?

 * task size: small, large, mixed
 * back pressure: eg, because tasks use a lot of memory
 * latency: eg, tasks coming from the network
 * efficiency under low load: waiting for tasks shouldn't burn the cpu,
simulated by timing a cpu-intensive task running in the background


#### The Queues and Implementations

Queues:
* [Conversant Disruptor](https://github.com/conversant/disruptor): a fast blocking queue
* [JcTools](https://github.com/JCTools/JCTools): psy-lob-saw's concurrent data structures
* [Kilim](https://github.com/kilim/kilim): fibers, continuations and message passing for java
* [Quasar](https://github.com/puniverse/quasar): fibers and message passing for java and kotlin
* [RxJava](https://github.com/ReactiveX/RxJava): reactive extensions for the JVM
* [the Java ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html)

Imperative:
* A single threaded for-each loop
* [Java 8 streams](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html)

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
sudo cpupower frequency-set -u 2.5ghz
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

any of the JMH `Params` can be set by setting the same system property of the same name,
eg `-Dmode=cost` is equivalent to `-p mode=cost`.
`Stream8` typically fails the hard-limit for scenarios with back pressure,
which results in an immediate (intentional) exit.


#### Methodology

These results are aggragates based on runs on several machines
* all using ubuntu 18.04 LTS
* aws runs use ubuntu's 11.0.4+11-1ubuntu2~18.04.3 jdk (openjdk-11-jdk-headless:amd64)
* on-prem runs use openjdk 12+33 jdk (installed from tarball)
* a small performance-preserving semi-random sampling of the jmh runs is
[stored in the `jmh.data` branch](https://github.com/nqzero/reactive-bench/blob/jmh.data/saved) that closely approximates these charts (within 1-3%)
* per-use-case (ie mode) performance is normalized by the fastest of the benchmarks for that use-case
* composite performance is the mean of the per-use-case performances
* finally, the results are the mean of the corresponding values for each of the machine types
* the (minimal) tuning was done based on the on-prem results only
* [./modes.m](modes.m) is an octave file for processing jmh csv files, eg to load and plot the saved data:
```
  git checkout jmh.data -- saved
  vs = printBench()` 
```

machine types:
* on-prem, i5-3570, 4 cores @ 2.5GHz throttle, 16G, 1600 runs
* aws, c5.large, 4 vcpu, 8G, 1920 runs
* aws, c5.2xlarge, 8vcpu, 16G, 3456 runs




## Meta

#### License

Apache 2.0 License

#### History

This project is based on a merged fork of two projects

* [Jose Paumard's kata from Devoxx 2015](https://github.com/JosePaumard/jdk8-stream-rx-comparison-reloaded)
* [David Karnok's (RxJava) direct implementation]
(https://github.com/akarnokd/akarnokd-misc/blob/master/src/jmh/java/hu/akarnokd/comparison/scrabble/ShakespearePlaysScrabbleWithDirect.java)

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

An Akka implementation would be especially welcomed.

#### Todo

* clean up the scenario selection / configuration code
* (in-progress) add a multiple-consumer mailbox to kilim
  (part of the motivation for this benchmark was to understand the limitations of kilim)
* the "Fair" implementations have better worst-case performance, eg to a DOS
  - would like to capture that more explicitly in one of the modes
* tuning, tuning, tuning
* understand the inconsistent back-pressure performance
* add a single-threaded RxJava implementation with back pressure
* base results on worst case timing, as opposed to median timing

