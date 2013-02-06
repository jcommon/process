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

import java.util.Stack;

/**
 * Allows reuse of memory buffers to prevent memory fragmentation over time.
 */
public class MemoryBufferPool {
  public static final int
      DEFAULT_INITIAL_SLICE_COUNT = 2
    , DEFAULT_SLICE_SIZE = 4096
    , DEFAULT_MAX_SLICE_COUNT = 1000
  ;

  public static final int
      INFINITE_SLICE_COUNT = -1
  ;

  private int slice_count;
  private final int slice_size;
  private final int max_slice_count;
  private final Object lock = new Object();
  private Stack<Pointer> used = new Stack<Pointer>();
  private Stack<Pointer> available = new Stack<Pointer>();

  public MemoryBufferPool() {
    this(DEFAULT_SLICE_SIZE, DEFAULT_INITIAL_SLICE_COUNT, DEFAULT_MAX_SLICE_COUNT);
  }

  public MemoryBufferPool(int sliceSize) {
    this(sliceSize, DEFAULT_INITIAL_SLICE_COUNT, DEFAULT_MAX_SLICE_COUNT);
  }

  public MemoryBufferPool(int sliceSize, int initialSliceCount, int maxSliceCount) {
    this.slice_size = sliceSize;
    this.max_slice_count = maxSliceCount;
    for(int i = 0; i < initialSliceCount; ++i) {
      available.push(allocateSlice());
    }
  }

  public void dispose() {
    synchronized (lock) {
      for(Pointer p : available) {
        PinnableMemory.dispose(p);
      }

      for(Pointer p : used) {
        PinnableMemory.dispose(p);
      }

      available.clear();
      used.clear();
      slice_count = 0;
    }
  }

  public int getSliceSize() {
    return slice_size;
  }

  public int getSliceCount() {
    return slice_count;
  }

  public int getMaximumSliceCount() {
    return max_slice_count;
  }

  public boolean isMaximumSliceCountInfinite() {
    return max_slice_count == INFINITE_SLICE_COUNT;
  }

  private Pointer allocateSlice() {
    if (!isMaximumSliceCountInfinite() && slice_count >= max_slice_count) {
      throw new IllegalStateException("Maximum slice count has been reached. Please release some slices from the pool before requesting more.");
    }

    ++slice_count;
    return PinnableMemory.pin(slice_size, new PinnableMemory.IPinListener() {
      @Override
      public boolean unpinned(final PinnableMemory memory) {
        //Don't dispose of the memory. We want to manage it ourselves.
        //Just return it to the pool. Returning false instructs PinnableMemory
        //to not dispose of the memory.
        returnToPool(memory);
        return false;
      }
    });
  }

  private void returnToPool(Pointer p) {
    synchronized (lock) {
      used.remove(p);
      available.push(p);
      --slice_count;
    }
  }

  public Pointer requestSlice() {
    synchronized (lock) {
      final Pointer p = !available.isEmpty() ? available.pop() : allocateSlice();
      used.push(p);
      return p;
    }
  }
}
