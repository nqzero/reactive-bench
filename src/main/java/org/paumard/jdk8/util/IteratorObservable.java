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


package org.paumard.jdk8.util;


import io.reactivex.Observable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public class IteratorObservable<E> {

	
	interface Wrapper<E> {
		E get() ;
		
		default Wrapper<E> set(E e) {
			return () -> e ;
		}
	}

	public static <E> Iterator<E> of(Observable<E> observable) {
		Objects.requireNonNull(observable) ;
		
		class Adapter implements Iterator<E>, Consumer<E> {
			
			Wrapper<Boolean> valueReady ;
            Wrapper<E> nextElement;
            
            public Adapter() {
            	observable.subscribe(
            		e -> nextElement.set(e), 
            		t -> valueReady.set(false), 
            		() -> valueReady.set(false)
            	) ;
            }

            @Override
            public void accept(E e) {
                valueReady.set(true);
                nextElement = () -> e;
            }

            @Override
            public boolean hasNext() {
                return valueReady.get();
            }

            @Override
            public E next() {
                if (!valueReady.get() && !hasNext())
                    throw new NoSuchElementException();
                else {
                    valueReady.set(false);
                    return nextElement.get() ;
                }
            }
        }

        return new Adapter();
	}
}
