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

package org.paumard.jdk8.gscollection;

import com.gs.collections.api.block.function.Function;
import com.gs.collections.api.block.function.primitive.IntFunction;
import com.gs.collections.api.block.function.primitive.LongFunction;
import com.gs.collections.api.block.predicate.Predicate;
import com.gs.collections.api.list.MutableList;
import com.gs.collections.api.map.MutableMap;
import com.gs.collections.impl.list.mutable.FastList;
import com.gs.collections.impl.list.mutable.primitive.CharArrayList;
import com.gs.collections.impl.map.sorted.mutable.TreeSortedMap;
import com.gs.collections.impl.set.mutable.UnifiedSet;
import org.openjdk.jmh.annotations.*;
import org.paumard.jdk8.bench.ShakespearePlaysScrabble;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author José
 */
public class ShakespearePlaysScrabbleWithGSCollections extends ShakespearePlaysScrabble {

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
    public MutableList<Entry<Integer, MutableList<String>>> measureThroughput() {

        // Function to compute the score of a given word
    	Function<Integer, Integer> scoreOfALetter =
    			letter -> letterScores[letter - 'a'];
            
        // score of the same letters in a word
        IntFunction<Entry<Integer, Long>> letterScore =
        		entry -> 
			        letterScores[entry.getKey() - 'a']*
			        Integer.min(
			            entry.getValue().intValue(), 
			            (int)scrabbleAvailableLetters[entry.getKey() - 'a']
			        );
        
        // Histogram of the letters in a given word
        Function<String, MutableMap<Integer, Long>> histoOfLetters =
        		word -> new CharArrayList(word.toCharArray())
        					.collect(c -> new Integer((int)c))
        					// .groupBy(letter -> letter) ;
        					.aggregateBy(
        							letter -> letter, 
        							() -> 0L, 
        							(value, letter) -> { return value + 1 ; }) ;
                
        // number of blanks for a given letter
        LongFunction<Entry<Integer, Long>> blank =
        		entry -> 
			        Long.max(
			            0L, 
			            entry.getValue() - 
			            scrabbleAvailableLetters[entry.getKey() - 'a']
			        );
                
        // number of blanks for a given word
        Function<String, Long> nBlanks =
        		word -> UnifiedSet.newSet(
	    					histoOfLetters.valueOf(word)
	    						.entrySet()
        				).sumOfLong(blank) ;
                
        // can a word be written with 2 blanks?
        Predicate<String> checkBlanks = word -> nBlanks.valueOf(word) <= 2;
        
        // score taking blanks into account
        Function<String, Integer> score2 =
        		word -> (int)UnifiedSet.newSet(
        					histoOfLetters.valueOf(word)
        						.entrySet()
        				).sumOfInt(letterScore) ;
                
        // Placing the word on the board
        // Building the streams of first and last letters
        Function<String, MutableList<Integer>> first3 =
        		word -> {
        			MutableList<Integer> list =
        			new CharArrayList(word.toCharArray())
        						.collect(c -> (int)c) ;
        			list = list.subList(0, Integer.min(list.size(), 3)) ;
        			return list ;
        		} ;
        		
		Function<String, MutableList<Integer>> last3 =
        		word -> new CharArrayList(word.toCharArray())
        						.collect(c -> new Integer((int)c))
        						.subList(Integer.max(0, word.length() - 4), word.length()) ;
        
        // Stream to be maxed
        Function<String, MutableList<Integer>> toBeMaxed =
        		word -> { 
        			MutableList<Integer> list = first3.valueOf(word) ;
        			list.addAll(last3.valueOf(word)) ;
        			return list ; 
        		} ;
            
        // Bonus for double letter
        IntFunction<String> bonusForDoubleLetter =
        	word -> toBeMaxed.valueOf(word)
        				.collect(scoreOfALetter)
        				.max() ;
            
        // score of the word put on the board
        Function<String, Integer> score3 =
        	word ->
        		2*(score2.valueOf(word) + bonusForDoubleLetter.intValueOf(word))
        		+ (word.length() == 7 ? 50 : 0);

        Function<Function<String, Integer>, MutableMap<Integer, MutableList<String>>> buildHistoOnScore =
        	score -> new UnifiedSet<String>(shakespeareWords)
        				.select(scrabbleWords::contains)
        				.select(checkBlanks)
        				.aggregateBy(
        						score, 
        						FastList::new, 
        						(list, value) -> {
        							list.add(value) ;
        							return list ;
        						}
        				) ;
                
        // best key / value pairs
        MutableList<Entry<Integer, MutableList<String>>> finalList =
        		new FastList<Entry<Integer,MutableList<String>>>(
        				new TreeSortedMap<Integer, MutableList<String>>(
        					Comparator.reverseOrder(),
        					buildHistoOnScore.valueOf(score3)
        				).entrySet()
        		).subList(0, 3) ;
        
        return finalList ;
    }
}
