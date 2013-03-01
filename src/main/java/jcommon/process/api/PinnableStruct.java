/*
  Copyright (C) 2013 the original author or authors.

  See the LICENSE.txt file distributed with this work for additional
  information regarding copyright ownership.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package jcommon.process.api;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.HashMap;
import java.util.HashSet;

/**
 *
 */
public abstract class PinnableStruct<T extends Structure> extends Structure {
  private static final HashSet<Pointer> pinned = new HashSet<Pointer>(2, 1.0f);
  private static final HashMap<Pointer, IPinListener> listeners = new HashMap<Pointer, IPinListener>(2, 1.0f);
  private static final Object pin_lock = new Object();
  private static final Object listeners_lock = new Object();

  public static interface IPinListener {
    void unpinned(Pointer instance);
  }

  public void dispose() {
    //Not used.
  }

  public void reuse(Pointer memory) {
    useMemory(memory);
    read();
  }

  public static <T extends Structure> T pin(final T instance) {
    return pin(instance, null);
  }

  public static <T extends Structure> T pin(final T instance, final IPinListener listener) {
    final Pointer ptr = instance.getPointer();

    synchronized (pin_lock) {
      pinned.add(ptr);
    }

    synchronized (listeners_lock) {
      if (listener != null) {
        listeners.put(ptr, listener);
      }
    }

    return instance;
  }

  public static Pointer pin(final Pointer ptr) {
    return pin(ptr, null);
  }

  public static Pointer pin(final Pointer ptr, final IPinListener listener) {

    synchronized (pin_lock) {
      pinned.add(ptr);
    }

    synchronized (listeners_lock) {
      if (listener != null) {
        listeners.put(ptr, listener);
      }
    }

    return ptr;
  }

  public static <T extends Structure> void unpin(final T instance) {
    PinnableStruct.unpin(instance.getPointer());
  }

  public static void unpin(final Pointer ptr) {
    synchronized (pin_lock) {
      pinned.remove(ptr);
    }

    final IPinListener listener;
    synchronized (listeners_lock) {
      listener = listeners.remove(ptr);
    }

    if (listener != null) {
      listener.unpinned(ptr);
      //If the listener returns false, then we do not
      //explicitly dispose of the memory -- the user will
      //need to take care of that himself.
    }
  }
}
