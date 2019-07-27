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
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Benchmark)
public class ShakespearePlaysScrabble {
    public static int numProc = Runtime.getRuntime().availableProcessors();
    public static boolean fast;


    @Param("ks")
    public String suffix;

    @Param("0")
    public int numHash;
    
    static {
        try { numProc = Integer.parseInt(System.getProperty("np")); }
        catch (Exception ex) {}
        fast = System.getProperty("fast") != null;
    }

    static public int numPool = Math.max(1,numProc-1);
    
    public static final int [] letterScores = {
    // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
       1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10} ;

    public static final int [] scrabbleAvailableLetters = {
     // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
        9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1} ;
    
    
    public Set<String> scrabbleWords = null ;
    private Set<String> words = null ;
    public Iterable<Stringx> shakespeareWords() {
        if (sleep < -1) return LimitSource::new;
        if (sleep == -1) return BurnSource::new;
        return sleep==0 ? Source::new : SleepSource::new;
    }
    AtomicInteger outstanding = new AtomicInteger();


    @Param("0")
    public int size;

    // limit the number of outstanding iterator objects using sleep
    @Param("80")
    public int soft = 80;

    // values greater than zero mean the soft limit is active
    @Param("0")
    public int sleep;

    // only the first letter is needed
    @Param("")
    public String mode;

    // hard limit on the number of soft limit sleeps
    static int MAX_YIELD = 1000;

    boolean startsWith(String txt) { return mode.startsWith(txt.substring(0,1)); }

    void getSoft() {
        try { soft = Integer.parseInt(mode.substring(1)); }
        catch (Exception ex) {}
    }
    void getLimit() {
        try { sleep = -Integer.parseInt(mode.substring(1)); }
        catch (Exception ex) {}
    }
    
    @Setup
    public void init() {
    	scrabbleWords = Util.readScrabbleWords() ;
        words = Util.readShakespeareWords();
        if (mode==null || mode.length()==0);
        else if (startsWith("all")) { suffix=""; numHash=1000; sleep=-100; getLimit(); }
        else if (startsWith("burn")) { sleep=-1; }
        else if (startsWith("cost")) { numHash=1000; }
        else if (startsWith("delay")) { sleep=1; getSoft(); }
        else if (startsWith("effort")) { sleep=10; getSoft(); }
        else System.out.println("mode not found, using defaults: " + mode);
        if (sleep <= 0)
            soft = 0;
    }

    class Source implements Iterator<Stringx> {
        Iterator<String> iter = words.iterator();
        public boolean hasNext() { return iter.hasNext(); }
        public Stringx next() { return new Stringx(iter.next()); }
    }

    class SleepSource implements Iterator<Stringx> {
        int maxOut = soft;
        int nyield;
        Iterator<String> iter = words.iterator();
        public boolean hasNext() { return iter.hasNext(); }
        public Stringx next() {
            try {
                int ii=0;
                for (; ii <= sleep && outstanding.get() >= maxOut; ii++)
                    Thread.sleep(ii < sleep ? 0:1);
                if (ii > sleep && ++nyield > MAX_YIELD) {
                    String msg = "number of yields exceeded - shutting down immediately";
                    new AssertionError(msg).printStackTrace();
                    System.exit(1);
                }
                outstanding.incrementAndGet();
                return new Stringx(iter.next());
            }
            catch (InterruptedException ex) {}
            return null;
        }
    }

    class BurnSource implements Iterator<Stringx> {
        int index;
        Thread [] burners = new Thread[numProc];
        Iterator<String> iter = words.iterator();
        {
            for (int ii=0; ii < burners.length; ii++)
                (burners[ii] = new Thread(() -> Blackhole.consumeCPU(100_000_000))).start();
        }
        public boolean hasNext() {
            try {
                Thread.sleep(1);
                if (index < 100)
                    return iter.hasNext();
                for (Thread thread : burners)
                    thread.join();
            }
            catch (InterruptedException ex) {}
            return false;
        }
        public Stringx next() {
            index++;
            return new Stringx(iter.next());
        }
    }
    class LimitSource implements Iterator<Stringx> {
        int index;
        int limit = -sleep;
        Iterator<String> iter = words.iterator();
        public boolean hasNext() {
            if (index < limit)
                return iter.hasNext();
            return false;
        }
        public Stringx next() {
            index++;
            return new Stringx(iter.next());
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
