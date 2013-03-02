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
import java.util.Map;

/**
 *
 */
public abstract class PinnableStruct<T extends Structure> extends Structure {
  private static final Map<Pointer, Object> pinned = new HashMap<Pointer, Object>(2, 1.0f);
  private static final Map<Pointer, IPinListener> listeners = new HashMap<Pointer, IPinListener>(2, 1.0f);
  private static final Object pin_lock = new Object();
  private static final Object listeners_lock = new Object();

  public static interface IPinListener<T extends Structure> {
    void unpinned(T instance);
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
      pinned.put(ptr, instance);
    }

    synchronized (listeners_lock) {
      if (listener != null) {
        listeners.put(ptr, listener);
      }
    }

    return instance;
  }

  public static <T extends Structure> T unpin(final T instance) {
    if (instance == null)
      return null;
    return PinnableStruct.unpin(instance.getPointer());
  }

  @SuppressWarnings("unchecked")
  public static <T extends Structure> T unpin(final Pointer ptr) {
    if (null == ptr) {
      return null;
    }

    final T value;
    synchronized (pin_lock) {
      value = (T)pinned.remove(ptr);
    }

    final IPinListener<T> listener;
    synchronized (listeners_lock) {
      listener = (IPinListener<T>)listeners.remove(ptr);
    }

    if (listener != null) {
      listener.unpinned(value);
      //If the listener returns false, then we do not
      //explicitly dispose of the memory -- the user will
      //need to take care of that himself.
    }

    return value;
  }
}
