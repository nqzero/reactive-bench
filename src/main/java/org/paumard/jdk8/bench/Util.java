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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {

	public static Set<String> readScrabbleWords() {
		Set<String> scrabbleWords = new HashSet<>() ;
        try (Stream<String> scrabbleWordsStream = Files.lines(Paths.get("files", "ospd.txt"))) {
            scrabbleWords.addAll(scrabbleWordsStream.map(String::toLowerCase).collect(Collectors.toSet()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return scrabbleWords ;
	}
	
	public static Set<String> readShakespeareWords() {
        Set<String> shakespeareWords = new HashSet<>() ;
        try (Stream<String> shakespeareWordsStream = Files.lines(Paths.get("files", "words.shakespeare.txt"))) {
            shakespeareWords.addAll(shakespeareWordsStream.map(String::toLowerCase).collect(Collectors.toSet()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return shakespeareWords ;
	}
}
