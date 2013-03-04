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
  private static final Map<Pointer, Object> tags = new HashMap<Pointer, Object>(2, 1.0f);
  private static final Map<Pointer, IPinListener> listeners = new HashMap<Pointer, IPinListener>(2, 1.0f);
  private static final Object pin_lock = new Object();
  private static final Object tag_lock = new Object();
  private static final Object listeners_lock = new Object();

  public static interface IPinListener<T extends Structure, O extends Object> {
    void unpinned(T instance, O tag);
  }

  public void dispose() {
    //Not used.
  }

  public void reuse(Pointer memory) {
    useMemory(memory);
    read();
  }

  public static <T extends Structure> T pin(final T instance) {
    return pin(instance, null, null);
  }

  public static <T extends Structure> T pin(final T instance, Object tag) {
    return pin(instance, tag, null);
  }

  public static <T extends Structure> T pin(final T instance, final IPinListener listener) {
    return pin(instance, null, listener);
  }

  public static <T extends Structure> T pin(final T instance, final Object tag, final IPinListener listener) {
    final Pointer ptr = instance.getPointer();

    synchronized (pin_lock) {
      pinned.put(ptr, instance);
      synchronized (tag_lock) {
        if (tag != null) {
          tags.put(ptr, tag);
        }
      }
    }

    synchronized (listeners_lock) {
      if (listener != null) {
        listeners.put(ptr, listener);
      }
    }

    return instance;
  }

  public static <O extends Object, T extends Structure> O untag(final T instance) {
    if (instance == null)
      return null;
    return PinnableStruct.untag(instance.getPointer());
  }

  @SuppressWarnings("unchecked")
  public static <O extends Object, T extends Structure> O untag(final Pointer ptr) {
    if (null == ptr) {
      return null;
    }

    final O value;
    synchronized (tag_lock) {
      value = (O)tags.remove(ptr);
    }

    return value;
  }

  public static <T extends Structure, O extends Object> T unpin(final T instance) {
    if (instance == null)
      return null;
    return PinnableStruct.unpin(instance.getPointer());
  }

  @SuppressWarnings("unchecked")
  public static <T extends Structure, O extends Object> T unpin(final Pointer ptr) {
    if (null == ptr) {
      return null;
    }

    final T value;
    final O tag;
    synchronized (pin_lock) {
      value = (T)pinned.remove(ptr);
    }

    synchronized (tag_lock) {
      tag = (O)tags.remove(ptr);
    }

    final IPinListener<T, O> listener;
    synchronized (listeners_lock) {
      listener = (IPinListener<T, O>)listeners.remove(ptr);
    }

    if (listener != null) {
      listener.unpinned(value, tag);
      //If the listener returns false, then we do not
      //explicitly dispose of the memory -- the user will
      //need to take care of that himself.
    }

    return value;
  }
}
