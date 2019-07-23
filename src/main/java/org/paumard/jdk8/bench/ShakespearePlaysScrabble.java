/*
 * Copyright (C) 2019 José Paumard
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

package org.paumard.jdk8.bench;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Param;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class ShakespearePlaysScrabble {

    
    public static final int [] letterScores = {
    // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
       1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10} ;

    public static final int [] scrabbleAvailableLetters = {
     // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
        9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1} ;
    
    
    public Set<String> scrabbleWords = null ;
    private Set<String> words = null ;
    public Iterable<Stringx> shakespeareWords() {
        return sleep==0 ? Source::new : SleepSource::new;
    }
    AtomicInteger outstanding = new AtomicInteger();


    @Param("256")
    public int size = 1<<8;

    @Param("256")
    public int soft = 1<<8;

    @Param("0")
    public int sleep = 0;

    static int MAX_YIELD = 1000;
    
    @Setup
    public void init() {
    	scrabbleWords = Util.readScrabbleWords() ;
        words = Util.readShakespeareWords();
    }

    class Source implements Iterator<Stringx> {
        Iterator<String> iter = words.iterator();
        public boolean hasNext() { return iter.hasNext(); }
        public Stringx next() { return new Stringx(iter.next()); }
    }

    class SleepSource implements Iterator<Stringx> {
        int maxOut = soft + 32;
        int nyield;
        Iterator<String> iter = words.iterator();
        public boolean hasNext() { return iter.hasNext(); }
        public Stringx next() {
            try {
                int ii=0;
                for (; ii <= sleep && outstanding.get() >= maxOut; ii++)
                    Thread.sleep(ii < sleep ? 0:1);
                if (ii > sleep && ++nyield > MAX_YIELD)
                    System.exit(1);
                outstanding.incrementAndGet();
                return new Stringx(iter.next());
            }
            catch (InterruptedException ex) {}
            return null;
        }
    }

    public class Stringx {
        public String data;
        public Stringx(String data) { this.data = data; }
        public void dispose() {
            outstanding.decrementAndGet();
        }
    }
}
