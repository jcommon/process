package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.api.ObjectPool;
import jcommon.process.api.PinnableStruct;

final class OverlappedPool {
  private final ObjectPool<OVERLAPPEDEX> pool;
  private final PinnableStruct.IPinListener<OVERLAPPEDEX> pin_listener;

  public OverlappedPool(int initialPoolSize) {
    this.pin_listener = new PinnableStruct.IPinListener<OVERLAPPEDEX>() {
      @Override
      public boolean unpinned(OVERLAPPEDEX instance) {
        pool.returnToPool(instance);
        return false;
      }
    };

    this.pool = new ObjectPool<OVERLAPPEDEX>(initialPoolSize, ObjectPool.INFINITE_POOL_SIZE, new ObjectPool.Allocator<OVERLAPPEDEX>() {
      @Override
      public OVERLAPPEDEX allocateInstance() {
        return OVERLAPPEDEX.pin(new OVERLAPPEDEX(), pin_listener);
      }

      @Override
      public void disposeInstance(OVERLAPPEDEX instance) {
        OVERLAPPEDEX.dispose(instance);
      }
    });
  }

  public void dispose() {
    pool.dispose();
  }

  public OVERLAPPEDEX requestInstance() {
    return pool.requestInstance();
  }

  public OVERLAPPEDEX requestInstance(final int operation) {
    return requestInstance(operation, null, 0);
  }

  public OVERLAPPEDEX requestInstance(final int operation, final Pointer buffer, final int buffer_size) {
    final OVERLAPPEDEX instance = requestInstance();
    instance.op = operation;
    instance.buffer = buffer;
    instance.bufferSize = buffer_size;
    return instance;
  }
}