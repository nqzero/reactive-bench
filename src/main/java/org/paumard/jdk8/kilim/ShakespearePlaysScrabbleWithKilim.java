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

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import kilim.ForkJoinScheduler;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.Task;

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
public class ShakespearePlaysScrabbleWithKilim extends ShakespearePlaysScrabble {
    static int numPool = Math.max(1, Scheduler.defaultNumberThreads - 1);
    static {
        Scheduler.setDefaultScheduler(new ForkJoinScheduler(numPool));
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

    class Worker extends Task<ArrayList<Count>> {
        int size = 1<<8;
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
    
    @SuppressWarnings("unused")
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(
        iterations=20
    )
    @Measurement(
        iterations=20
    )
    @Fork(5)
    public List<Entry<Integer, List<String>>> measureThroughput() {
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
        ShakespearePlaysScrabbleWithKilim s = new ShakespearePlaysScrabbleWithKilim();
        s.init();
        System.out.println(s.measureThroughput());
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
