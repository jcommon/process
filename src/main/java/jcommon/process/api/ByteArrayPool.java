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

/**
 */
@SuppressWarnings("unused")
public final class ByteArrayPool {
  private final ObjectPool<Buffer> pool;
  private final int buffer_slice_size;

  public ByteArrayPool(final int initialPoolSize, final int bufferSliceSize) {
    this.buffer_slice_size = bufferSliceSize;

    this.pool = new ObjectPool<Buffer>(initialPoolSize, ObjectPool.INFINITE_POOL_SIZE, new ObjectPool.Allocator<Buffer>() {
      @Override
      public Buffer allocateInstance() {
        return new Buffer(bufferSliceSize, pool);
      }

      @Override
      public void disposeInstance(final Buffer instance) {
        instance.dispose();
      }
    });
  }

  public boolean isPoolSizeInfinite() {
    return pool.isMaximumPoolSizeInfinite();
  }

  public int getPoolSize() {
    return pool.getPoolSize();
  }

  public int getBufferSliceSize() {
    return buffer_slice_size;
  }

  public void dispose() {
    pool.dispose();
  }

  public Buffer requestInstance() {
    synchronized (pool.getLock()) {
      return pool.requestInstance();
    }
  }

  public void returnToPool(final Buffer buffer) {
    pool.returnToPool(buffer);
  }

  public static final class Buffer {
    private final byte[] buffer;
    private final int buffer_size;
    private final ObjectPool<Buffer> pool;

    public Buffer(final int buffer_size, final ObjectPool<Buffer> pool) {
      this.pool = pool;
      this.buffer_size = buffer_size;
      this.buffer = new byte[buffer_size];
    }

    public byte[] getBuffer() {
      return buffer;
    }

    public int getBufferSize() {
      return buffer_size;
    }

    public void dispose() {
    }
  }
}
