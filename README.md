# Shakespeare plays Reactive Scrabble with Queues

Use JMH to benchmark several queue libraries and their ability to coordinate work
on a number of threads.


## Queues

* [Conversant Disruptor](https://github.com/conversant/disruptor): a fast blocking queue
* Java 8 streams
* [JcTools](https://github.com/JCTools/JCTools): psy-lob-saw's concurrent data structures
* [Kilim](https://github.com/kilim/kilim): fibers, continuations and message passing for java
* [Quasar](https://github.com/puniverse/quasar): fibers and message passing for java and kotlin
* [RxJava](https://github.com/ReactiveX/RxJava): reactive extensions for the JVM

Note:
the intent of these implementations is that buffering should be limited to
simulate a live connection in which real resources are consumed
and show the advantages of a reactive approach.

* for Java 8 streams, it's not clear to what degree the iterable is buffered
* I'm not experienced enough with RxJava to know how much buffering occurs
* the other libraries provide obvious buffer sizes (which I'm trusting)

## Running

```
sudo cpupower frequency-set -u 2.8ghz
java -version 2>&1 | head -n1 | grep -q "version\W*1.8" && git checkout quasar7
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
quasar="-javaagent:$(echo $cp | grep -o '[^:]*' | grep quasar-core)"
mvn package
java $quasar -jar target/benchmarks.jar
java $quasar -cp target/classes:$cp direct.ShakespearePlaysScrabbleWithQueues
```

Notes:

* depending on your machine, you may experience thermal throttling.
Adjust the first line accordingly.
* quasar 0.8 supports java 9 and later, and is incompatible with java 8.
this is the default. for java 8, the above checks out the quasar7 tag


## Flags

For the direct implementations, some jvm `-D` flags are accepted.
* `-Dsuffix=xxx`: for words matching this suffix, hash the word and modify the score.
try "-Dsuffix=ks"
* `-Dnh=1000`: number of sha-256 hashes to perform. default is 1000
* `-Dfast`: only store the 3 best scores at any time
* `-Dnp=4`: number of cpus to assume. default is number of available cpus
* `-Dsize=10`: the number of bits of buffer to use. default is 10

these flags can be useful for understanding how the implementations perform.


## Results





## License and History

Apache 2.0 licence

This project is a merged fork of two projects

* [Jose Paumard's kata from Devoxx 2015](https://github.com/JosePaumard/jdk8-stream-rx-comparison-reloaded)
* [David Karnok's (RxJava) direct implementation](https://github.com/akarnokd/akarnokd-misc/blob/master/src/jmh)

This project adds several multi-threaded implementations using different queues
for communicating between the threads.

This bench uses two data files: `ospd.txt` and `words.shakespeare.txt`.
They can be freely downloaded from Robert Sedgewick page here: http://introcs.cs.princeton.edu/java/data/.
Those two data sets files are under the copyright of their authors, and provided for convenience only.


