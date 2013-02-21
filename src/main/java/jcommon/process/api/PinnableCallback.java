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

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class PinnableCallback<T extends Callback> {
  private static final Set<Callback> pinned = new HashSet<Callback>(2, 1.0f);
  private static final Map<Callback, IPinListener> listeners = new HashMap<Callback, IPinListener>(2, 1.0f);
  private static final Object pin_lock = new Object();
  private static final Object listeners_lock = new Object();

  public static interface IPinListener<C extends Callback> {
    boolean unpinned(C callback);
  }

  public void dispose() {
    //Not used.
  }

  public static <C extends Callback> C pin(final C callback) {
    return pin(callback, null);
  }

  public static <C extends Callback> C pin(final C callback, final IPinListener<C> listener) {
    synchronized (pin_lock) {
      pinned.add(callback);
    }

    synchronized (listeners_lock) {
      if (listener != null) {
        listeners.put(callback, listener);
      }
    }

    return callback;
  }

  public static <C extends Callback> void unpin(final C callback) {
    if (callback == null) {
      return;
    }

    final IPinListener listener;

    synchronized (pin_lock) {
      pinned.remove(callback);
    }

    synchronized (listeners_lock) {
      listener = listeners.remove(callback);
    }

    if (listener != null) {
      if (listener.unpinned(callback)) {
        if (callback instanceof PinnableCallback) {
          ((PinnableCallback)callback).dispose();
        }
      }
      //If the listener returns false, then we do not
      //explicitly call dispose on the callback -- the user will
      //need to take care of that himself.
    } else {
      if (callback instanceof PinnableCallback) {
        ((PinnableCallback)callback).dispose();
      }
    }
  }

  public static <C extends Callback> void dispose(final Callback callback) {
    if (callback != null && (callback instanceof PinnableCallback)) {
      ((PinnableCallback)callback).dispose();
      return;
    }
  }
}
