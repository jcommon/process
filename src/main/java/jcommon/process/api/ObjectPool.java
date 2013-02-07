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

import java.util.Stack;

/**
 * Allows reuse of memory buffers to prevent memory fragmentation over time.
 */
@SuppressWarnings("unused")
public class ObjectPool<T> {
  public static final int
      DEFAULT_INITIAL_POOL_SIZE = 2
    , DEFAULT_MAX_POOL_SIZE = 100
  ;

  public static final int
      INFINITE_POOL_SIZE = -1
  ;

  public static interface IAllocator<T> {
    T allocateInstance();
    void reclaimInstance(T instance);
    void disposeInstance(T instance);
  }

  private int pool_size;
  private final int max_pool_size;
  private final Object lock = new Object();
  private final Stack<T> used = new Stack<T>();
  private final Stack<T> available = new Stack<T>();
  private final IAllocator<T> allocator;
  private boolean is_disposed = false;

  public ObjectPool(final IAllocator<T> allocator) {
    this(DEFAULT_INITIAL_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, allocator);
  }

  public ObjectPool(final int initialPoolSize, final int maxPoolSize, final IAllocator<T> allocator) {
    if (allocator == null) {
      throw new NullPointerException("allocator cannot be null");
    }

    this.allocator = allocator;
    this.max_pool_size = maxPoolSize;

    for(int i = 0; i < initialPoolSize; ++i) {
      available.push(allocateInstance());
    }
  }

  public final void dispose() {
    synchronized (lock) {
      is_disposed = true;

      for(T a : available) {
        disposeInstance(a);
      }

      for(T u : used) {
        disposeInstance(u);
      }

      available.clear();
      used.clear();

      pool_size = 0;
    }

    innerDispose();
  }

  protected void innerDispose() {
    //Do nothing. Place holder for classes extending this one.
  }

  public final boolean isDisposed() {
    return is_disposed;
  }

  public final int getPoolSize() {
    return pool_size;
  }

  public final int getMaximumPoolSize() {
    return max_pool_size;
  }

  public final boolean isMaximumPoolSizeInfinite() {
    return max_pool_size == INFINITE_POOL_SIZE;
  }

  protected T allocateInstance() {
    if (!isMaximumPoolSizeInfinite() && pool_size >= max_pool_size) {
      throw new IllegalStateException("Maximum pool size has been reached. Please release some instances from the pool before requesting more.");
    }

    ++pool_size;
    return allocator.allocateInstance();
  }

  protected void reclaimInstance(T instance) {
    allocator.reclaimInstance(instance);
  }

  protected void disposeInstance(T instance) {
    allocator.disposeInstance(instance);
  }

  public final T requestInstance() {
    synchronized (lock) {
      if (isDisposed()) {
        throw new IllegalStateException("Pool has been disposed. Unable to allocate more instances.");
      }

      final T t = !available.isEmpty() ? available.pop() : allocateInstance();
      used.push(t);
      return t;
    }
  }

  public final void returnToPool(T t) {
    synchronized (lock) {
      used.remove(t);
      available.push(t);
      --pool_size;
    }
    reclaimInstance(t);
  }
}
