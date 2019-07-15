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

import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.SpinPolicy;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import kilim.ForkJoinScheduler;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;
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
    static int numPool = Math.max(1, Scheduler.defaultNumberThreads-1);
    static int size = 1<<10;

    interface Jmh {
        public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException;
    }
    public static abstract class Base extends ShakespearePlaysScrabbleWithKilim implements Jmh {
        void doMain() throws Exception {
            init();
            System.out.println(measureThroughput());
        }
    }

    @TearDown(Level.Trial)
    public void doSetup() {
        try { Thread.sleep(12000); }
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

    public static class Threaded extends Base {
        SpmcArrayQueue<String> queue;
        @Benchmark
        public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {
            queue = new SpmcArrayQueue(size);
            Runner [] actors = new Runner[numPool];
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Runner()).start();
            for (String word : shakespeareWords)
                while (! queue.offer(word));
            for (int ii=0; ii < actors.length; ii++)
                while (! queue.offer(stop));

            TreeMap<Integer, List<String>> treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
            for (Runner actor : actors) {
                actor.join();
                for (Count count : actor.list)
                    addWord(count.num, count.word, treemap);
            }
            return getList(treemap);
        }
        class Runner extends Thread {
            ArrayList<Count> list = new ArrayList<>();
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        list.add(new Count(num,word));
                }
            }
        }
    }

    public static class Flat extends Base {
        @Benchmark
        public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {
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

            TreeMap<Integer, List<String>> treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
            for (Runner actor : actors) {
                actor.join();
                for (Count count : actor.list)
                    addWord(count.num, count.word, treemap);
            }
            return getList(treemap);
        }
        class Runner extends Thread {
            SpscArrayQueue<String> queue = new SpscArrayQueue(size);
            ArrayList<Count> list = new ArrayList<>();
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        list.add(new Count(num,word));
                }
            }
        }
    }
    
    public static class Conversant extends Base {
        @Benchmark
        public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {
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

            TreeMap<Integer, List<String>> treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
            for (Runner actor : actors) {
                actor.join();
                for (Count count : actor.list)
                    addWord(count.num, count.word, treemap);
            }
            return getList(treemap);
        }
        class Runner extends Thread {
            private DisruptorBlockingQueue<String> queue =
                    new DisruptorBlockingQueue<>(size, SpinPolicy.SPINNING);
            ArrayList<Count> list = new ArrayList<>();
            public void run() {
                for (String word; (word = queue.poll()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        list.add(new Count(num,word));
                }
            }
        }
    }
    
    
    public static class Direct extends Base {
        @Benchmark
        public List<Entry<Integer, List<String>>> measureThroughput() {
            TreeMap<Integer, List<String>> treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
            for (String word : shakespeareWords) {
                Integer num = getWord(word);
                if (num != null)
                    addWord(num, word, treemap);
            }
            return getList(treemap);
        }
    }

    public static class Kilim extends Base {
        static {
            Scheduler.setDefaultScheduler(new ForkJoinScheduler(numPool));
        }
        @Benchmark
        public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {
            Worker [] actors = new Worker[numPool];
            int target = 0;
            for (int ii=0; ii < actors.length; ii++)
                (actors[ii] = new Worker()).start();
            for (String word : shakespeareWords) {
                if (target==numPool) target = 0;
                actors[target++].box.putb(word);
            }
            for (Worker actor : actors)
                actor.box.putb(stop);

            TreeMap<Integer, List<String>> treemap = new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
            for (Worker actor : actors)
                for (Count count : actor.joinb().result)
                    addWord(count.num, count.word, treemap);
            return getList(treemap);
        }

        class Worker extends Task<ArrayList<Count>> {
            Mailbox<String> box = new Mailbox(size,size);

            public void execute() throws Pausable {
                ArrayList<Count> list = new ArrayList<>();
                for (String word; (word = box.get()) != stop;) {
                    Integer num = getWord(word);
                    if (num != null)
                        list.add(new Count(num,word));
                }
                exit(list);
            }
        }
    }

    Integer getWord(String word) {
            if (scrabbleWords.contains(word)) {
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
                    return sum2;
                }
            }
            return null;
    }
    void addWord(Integer sum2,String word,TreeMap<Integer, List<String>> treemap) {
        {
            {
                {
                    Integer key = sum2;

                    List<String> list = treemap.get(key) ;
                    if (list == null) {
                        list = new ArrayList<>() ;
                        treemap.put(key, list) ;
                    }
                    list.add(word) ;
                }
            }
        }
    }
    
    List<Entry<Integer, List<String>>> getList(TreeMap<Integer, List<String>> treemap) {
        List<Entry<Integer, List<String>>> list = new ArrayList<Entry<Integer, List<String>>>();
        
        int i = 4;
        for (Entry<Integer, List<String>> e : treemap.entrySet()) {
            if (--i == 0) {
                break;
            }
            list.add(e);
        }

        return list;
    }

    public static void main(String[] args) throws Exception {
        new Threaded().doMain();
        new Flat().doMain();
        new Conversant().doMain();
        new Kilim().doMain();
        new Direct().doMain();
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


*/