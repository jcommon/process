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

/**
 * Allows reuse of memory buffers to prevent memory fragmentation over time.
 */
@SuppressWarnings("unused")
public final class MemoryPool {
  public static final int
      DEFAULT_INITIAL_SLICE_COUNT = (int)(Runtime.getRuntime().availableProcessors() * 1.5)
    , DEFAULT_SLICE_SIZE          = 1024
    , DEFAULT_MAX_SLICE_COUNT     = 1000
  ;

  public static final int
      INFINITE_SLICE_COUNT = -1
  ;

  private final int slice_size;
  private final ObjectPool<Pointer> pool;
  private final PinnableMemory.IPinListener pin_listener;

  public MemoryPool() {
    this(DEFAULT_SLICE_SIZE, DEFAULT_INITIAL_SLICE_COUNT, DEFAULT_MAX_SLICE_COUNT);
  }

  public MemoryPool(int sliceSize) {
    this(sliceSize, DEFAULT_INITIAL_SLICE_COUNT, DEFAULT_MAX_SLICE_COUNT);
  }

  public MemoryPool(final int sliceSize, int initialSliceCount, int maxSliceCount) {
    this.slice_size = sliceSize;

    this.pin_listener = new PinnableMemory.IPinListener() {
      @Override
      public boolean unpinned(final PinnableMemory memory) {
        //Don't dispose of the memory. We want to manage it ourselves.
        //Just return it to the pool. Returning false instructs PinnableMemory
        //to not dispose of the memory.
        pool.returnToPool(memory);
        return false;
      }
    };

    this.pool = new ObjectPool<Pointer>(initialSliceCount, maxSliceCount, new ObjectPool.Allocator<Pointer>() {
      @Override
      public Pointer allocateInstance() {
        return PinnableMemory.pin(slice_size, pin_listener);
      }

      @Override
      public void reclaimInstance(Pointer instance) {
        //Do nothing b/c we've assigned a listener to the memory for when it's unpinned.
        //The listener will automatically clean up for us.
        //See the pin_listener#unpinned() method.
      }

      @Override
      public void disposeInstance(Pointer p) {
        PinnableMemory.dispose(p);
      }
    });
  }

  public void dispose() {
    pool.dispose();
  }

  public int getSliceSize() {
    return slice_size;
  }

  public int getSliceCount() {
    return pool.getPoolSize();
  }

  public int getMaximumSliceCount() {
    return pool.getMaximumPoolSize();
  }

  public boolean isMaximumSliceCountInfinite() {
    return pool.isMaximumPoolSizeInfinite();
  }

  public Pointer requestSlice() {
    return pool.requestInstance();
  }
}
