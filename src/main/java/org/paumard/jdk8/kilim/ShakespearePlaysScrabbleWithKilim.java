/*
 * Copyright (C) 2019 Jose Paumard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.paumard.jdk8.kilim;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.conversantmedia.util.concurrent.SpinPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kilim.ForkJoinScheduler;
import kilim.Mailbox;
import kilim.MailboxSPSC;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import org.openjdk.jmh.annotations.*;
import org.paumard.jdk8.bench.ShakespearePlaysScrabble;

/**
 * Shakespeare plays Scrabble, using Kilim actors with backpressure.
 * several actors are spawned,
 * the words are distributed to them,
 * each returns a list of matches,
 * the matches are aggregated,
 * and the best results are returned
 * 
 * all word-processing code is copied verbatim from akarnokd's Direct implementation
 * 
 * kilim is a framework for building actors
 * 
 * 
 * @author José
 * @author akarnokd
 * @author nqzero
 */
@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations=12, time=1)
@Measurement(iterations=12, time=1)
public abstract class ShakespearePlaysScrabbleWithKilim extends ShakespearePlaysScrabble {
    static int numPool = Math.max(1, Runtime.getRuntime().availableProcessors()-1);
    static int size = 1<<10;
    static int delay;
    static boolean fast;
    static String suffix;
    static int numHash = 100;
    TreeMap<Integer, List<String>> treemap;
    int smallest;
    int numSave = 3;
    Count lastCount = new Count(0, null);

    static {
        try { delay = Integer.parseInt(System.getProperty("d")); }
        catch (Exception ex) { delay = 12000; }
        try { numPool = Integer.parseInt(System.getProperty("np")); }
        catch (Exception ex) {}
        try { size = 1<<Integer.parseInt(System.getProperty("size")); }
        catch (Exception ex) {}
        fast = System.getProperty("fast") != null;
        suffix = System.getProperty("suffix");
        try { numHash = Integer.parseInt(System.getProperty("nh")); }
        catch (Exception ex) {}
    }
    static ThreadLocal<MessageDigest> digest = new ThreadLocal();
    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    int hash(String word) {
        int score = 0;
        if (numHash > 0 && suffix != null && word.endsWith(suffix)) {
            MessageDigest digest2 = digest.get();
            if (digest2==null)
                digest.set(digest2 = digest());
            for (int ii=0; ii < numHash; ii++) {
                byte[] hash = digest2.digest(word.getBytes(StandardCharsets.UTF_8));
                score += hash[0] < 32 ? 1:0;
                word += "a";
            }
        }
        return score;
    }

    interface Jmh {
        public Object measureThroughput() throws InterruptedException;
    }
    public static abstract class Base extends ShakespearePlaysScrabbleWithKilim implements Jmh {
        void doMain() throws Exception {
            init();
            setup();
            System.out.println(measureThroughput());
        }
    }

    @Setup(Level.Invocation)
    public void setup() {
        treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
    }

    @TearDown(Level.Trial)
    public void doSetup() {
        try { Thread.sleep(delay); }
        catch (InterruptedException ex) {}
    }

    static class Count {
        int num;
        String word;

        public Count(int num,String word) {
            this.num = num;
            this.word = word;
        }
        
    }
    String stop = new String();

    public static class JctoolsFair extends Base {
        SpmcArrayQueue<String> queue;
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            queue = new SpmcArrayQueue(size);
            Runner [] actors = new Runner[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Runner()).start();
            for (String word : shakespeareWords)
                while (! queue.offer(word));
            for (int ii=0; ii < actors.length; ii++)
                while (! queue.offer(stop));

            for (Runner actor : actors)
                actor.join();
            queue = null;
            return getList();
        }
        class Runner extends Thread {
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
            }
        }
    }

    public static class Jctools extends Base {
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Runner [] actors = new Runner[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Runner()).start();
            int target = 0;
            for (String word : shakespeareWords) {
                if (++target==numPool) target = 0;
                while (!actors[target].queue.offer(word));
            }
            for (int ii=0; ii < actors.length; ii++)
                while (! actors[ii].queue.offer(stop));

            for (Runner actor : actors)
                actor.join();
            return getList();
        }
        class Runner extends Thread {
            SpscArrayQueue<String> queue = new SpscArrayQueue(size);
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
            }
        }
    }

    public static class Conversant extends Base {
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Runner [] actors = new Runner[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Runner()).start();
            int target = 0;
            for (String word : shakespeareWords) {
                if (++target==numPool) target = 0;
                actors[target].queue.put(word);
            }
            for (int ii=0; ii < actors.length; ii++)
                actors[ii].queue.put(stop);

            for (Runner actor : actors)
                actor.join();
            return getList();
        }
        class Runner extends Thread {
            private DisruptorBlockingQueue<String> queue =
                    new DisruptorBlockingQueue<>(size, SpinPolicy.WAITING);
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
            }
        }
    }

    public static class Push extends Base {
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Runner [] actors = new Runner[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Runner()).start();
            int target = 0;
            for (String word : shakespeareWords) {
                if (++target==numPool) target = 0;
                actors[target].queue.put(word);
            }
            for (int ii=0; ii < actors.length; ii++)
                actors[ii].queue.put(stop);

            for (Runner actor : actors)
                actor.join();
            return getList();
        }
        class Runner extends Thread {
            private PushPullBlockingQueue<String> queue =
                    new PushPullBlockingQueue<>(size, SpinPolicy.WAITING);
            ArrayList<Count> list = new ArrayList<>();
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
            }
        }
    }
    
    public static class Direct extends Base {
        @Benchmark
        public Object measureThroughput() {
            for (String word : shakespeareWords) {
                Integer num = getWord(word);
                if (num != null)
                    addWord(num, word);
            }
            return getList();
        }
    }

    public static class Quasar extends Base {
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Worker [] actors = new Worker[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Worker()).start();
            try {
                new Fiber<Void>(() -> {
                    int target = 0;
                    for (String word : shakespeareWords) {
                        if (++target==actors.length) target = 0;
                        actors[target].box.send(word);
                    }
                    for (Worker actor : actors)
                        actor.box.send(stop);
                }).start().joinNoSuspend();
                for (Worker actor : actors)
                    actor.joinNoSuspend();
            }
            catch (ExecutionException ex) {}

            return getList();
        }

        class Worker extends Fiber<Void> {
            Channel<String> box = Channels.newChannel(size,OverflowPolicy.BACKOFF,true,true);

            protected Void run() throws SuspendExecution,InterruptedException {
                for (String word; (word = box.receive()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
                return null;
            }
        }
    }

    public static class QuasarFair extends Base {
        Channel<String> box;
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            box = Channels.newChannel(size,OverflowPolicy.BACKOFF,true,false);
            Worker [] actors = new Worker[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Worker()).start();
            try {
                new Fiber<Void>(() -> {
                    for (String word : shakespeareWords)
                        box.send(word);
                    for (Worker actor : actors)
                        box.send(stop);
                }).start().joinNoSuspend();
                for (Worker actor : actors)
                    actor.joinNoSuspend();
            }
            catch (ExecutionException ex) {}

            box = null;
            return getList();
        }

        class Worker extends Fiber<Void> {

            protected Void run() throws SuspendExecution,InterruptedException {
                for (String word; (word = box.receive()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
                return null;
            }
        }
    }

    public static class Kilim extends Base {
        static {
            Scheduler.setDefaultScheduler(new ForkJoinScheduler(-1));
        }
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Worker [] actors = new Worker[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Worker()).start();
            Task.fork(() -> {
                int target = 0;
                for (String word : shakespeareWords) {
                    if (++target==actors.length) target = 0;
                    actors[target].box.put(word);
                }
                for (Worker actor : actors)
                    actor.box.put(stop);
            }).joinb();

            for (Worker actor : actors)
                actor.joinb();
            return getList();
        }

        class Worker extends Task<Void> {
            MailboxSPSC<String> box = new MailboxSPSC(size);

            public void execute() throws Pausable {
                for (String word; (word = box.get()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        addWord(num,word);
                }
            }
        }
    }

    public static class Movie extends Base {
        static {
            Scheduler.setDefaultScheduler(new ForkJoinScheduler(-1));
        }
        @Benchmark
        public Object measureThroughput() throws InterruptedException {
            Actors<String> actors = new Actors<String>(0, size, word -> {
                Integer num = getWord(word);
                if (num != null)
                    addWord(num,word);
            });
            Task.fork(() -> {
                for (String word : shakespeareWords)
                    actors.put(word);
                actors.join();
            }).joinb();
            return getList();
        }

        static class Actors<UU> {
            Actor [] actors;
            Consumer<UU> action;
            int target;
            Object stop2 = new Object();
            int inc() {
                if (++target==actors.length) target = 0;
                return target;
            }
            void place(UU value) throws Pausable {
                actors[inc()].box.put(value);
            }
            void put(UU value) throws Pausable {
                for (int ii=0; ii < actors.length; ii++)
                    if (actors[inc()].box.putnb(value)) return;
                place(value);
            }
            void join() throws Pausable {
                for (Actor actor : actors)
                    actor.box.put(stop2);
                for (Actor actor : actors)
                    actor.join();
            }

            public Actors(Consumer<UU> action) {
                this(0,1<<6,action);
            }
            public Actors(int num,int size,Consumer<UU> action) {
                this.action = action;
                if (num <= 0)
                    num = Math.max(1,Scheduler.defaultScheduler.numThreads()+num);
                actors = new Actor[num];
                for (int ii=0; ii < actors.length; ii++) {
                    actors[ii] = new Actor();
                    actors[ii].box = new MailboxSPSC(size);
                    actors[ii].ctrl = this;
                    actors[ii].start();
                }
            }
        }
        static class Actor<UU> extends Task<Void> {
            MailboxSPSC<UU> box;
            Actors ctrl;
            public void execute() throws Pausable {
                for (UU word; (word = box.get()) != ctrl.stop2;)
                    ctrl.action.accept(word);
            }
        }
    }

    Integer getWord(String word) {
            if (scrabbleWords.contains(word)) {
                int hash = hash(word);
                HashMap<Integer, MutableLong> wordHistogram = new LinkedHashMap<>();
                for (int i = 0; i < word.length(); i++) {
                    MutableLong newValue = wordHistogram.get((int)word.charAt(i)) ;
                    if (newValue == null) {
                        newValue = new MutableLong();
                        wordHistogram.put((int)word.charAt(i), newValue);
                    }
                    newValue.incAndSet();
                }
                long sum = 0L;
                for (Entry<Integer, MutableLong> entry : wordHistogram.entrySet()) {
                    sum += Long.max(0L, entry.getValue().get() -
                                scrabbleAvailableLetters[entry.getKey() - 'a']);
                }
                boolean b = sum <= 2L;

                if (b) {
                    // redo the histogram?!
//                    wordHistogram = new HashMap<>();
//                    for (int i = 0; i < word.length(); i++) {
//                        MutableLong newValue = wordHistogram.get((int)word.charAt(i)) ;
//                        if (newValue == null) {
//                            newValue = new MutableLong();
//                            wordHistogram.put((int)word.charAt(i), newValue);
//                        }
//                        newValue.incAndSet();
//                    }

                    int sum2 = 0;
                    for (Map.Entry<Integer, MutableLong> entry : wordHistogram.entrySet()) {
                        sum2 += letterScores[entry.getKey() - 'a'] *
                                Integer.min(
                                (int)entry.getValue().get(),
                                scrabbleAvailableLetters[entry.getKey() - 'a']
                            );
                    }
                    int max2 = 0;
                    for (int i = 0; i < 3 && i < word.length(); i++) {
                        max2 = Math.max(max2, letterScores[word.charAt(i) - 'a']);
                    }

                    for (int i = 3; i < word.length(); i++) {
                        max2 = Math.max(max2, letterScores[word.charAt(i) - 'a']);
                    }
                    
                    sum2 += max2;
                    sum2 = 2 * sum2 + (word.length() == 7 ? 50 : 0);
                    return sum2 + hash;
                }
            }
            return null;
    }
    synchronized void addWord(Integer sum2,String word) {
        {
            {
                {
                    Integer key = sum2;
                    boolean big = fast && treemap.size() >= numSave;
                    if (big && key < treemap.lastKey()) return;

                    List<String> list = treemap.get(key) ;
                    if (list == null) {
                        list = new ArrayList<>() ;
                        if (big)
                            treemap.pollLastEntry();
                        treemap.put(key, list) ;
                    }
                    list.add(word);
                }
            }
        }
    }
    
    Object getList() {
        List<Entry<Integer, List<String>>> list = new ArrayList();
        int i = 4;
        for (Entry<Integer, List<String>> e : treemap.entrySet()) {
            if (--i == 0)
                break;
            list.add(e);
        }
        treemap = null;
        return list;
    }

    public static void main(String[] args) throws Exception {
        new Jctools().doMain();
        new JctoolsFair().doMain();
        new Conversant().doMain();
        new Push().doMain();
        new Kilim().doMain();
        new Movie().doMain();
        new Direct().doMain();
        new Quasar().doMain();
    }

    static class MutableLong {
        long value;
        long get() {
            return value;
        }

        MutableLong set(long l) {
            value = l;
            return this;
        }

        MutableLong incAndSet() {
            value++;
            return this;
        }

        MutableLong add(MutableLong other) {
            value += other.value;
            return this;
        }
    }
}

/*

num-threads: NT is slower than NT-1 for all, and much slower for Conv and Flat
conversant: at best marginally faster with jdk10 classifier, prolly no change
java 8 might be a bit faster

java 8, 8 bits
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt    5  4.671 ± 0.983  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt    5  7.164 ± 0.977  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt    5  4.661 ± 0.971  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt    5  4.065 ± 0.236  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt    5  4.919 ± 0.726  ms/op

java 12, 8 bits
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt    5  4.977 ± 0.777  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt    5  7.453 ± 0.106  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt    5  5.227 ± 0.853  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt    5  4.257 ± 0.383  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt    5  4.709 ± 0.758  ms/op

java 12, 10 bits
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt    5  5.010 ± 0.197  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt    5  7.443 ± 0.171  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt    5  5.129 ± 1.033  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt    5  3.965 ± 0.155  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt    5  4.768 ± 0.861  ms/op

java 12, 10 bits, without jdk10 classifier
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt    5  5.249 ± 0.451  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt    5  7.544 ± 0.248  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt    5  5.202 ± 0.611  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt    5  4.029 ± 0.203  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt    5  4.729 ± 0.761  ms/op

java 12, 10 bits, w jdk10, 5*12+12
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt   60  4.741 ± 0.097  ms/op
java 12, 10 bits, w/o jdk10, 5*12+12
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt   60  4.811 ± 0.204  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt   60  7.171 ± 0.129  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt   60  4.554 ± 0.109  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt   60  4.112 ± 0.040  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt   60  4.576 ± 0.059  ms/op

java 8, 10 bits, w/o jdk10, 5*12+12
ShakespearePlaysScrabbleWithKilim.Conversant.measureThroughput  avgt   60  4.761 ± 0.182  ms/op
ShakespearePlaysScrabbleWithKilim.Direct.measureThroughput      avgt   60  6.453 ± 0.102  ms/op
ShakespearePlaysScrabbleWithKilim.Flat.measureThroughput        avgt   60  4.320 ± 0.106  ms/op
ShakespearePlaysScrabbleWithKilim.Kilim.measureThroughput       avgt   60  4.008 ± 0.029  ms/op
ShakespearePlaysScrabbleWithKilim.Threaded.measureThroughput    avgt   60  4.636 ± 0.046  ms/op
ShakespearePlaysScrabbleWithKilim.Movie.measureThroughput       avgt   60  4.047 ± 0.034  ms/op


*/