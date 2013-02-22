package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.api.ObjectPool;
import jcommon.process.api.PinnableStruct;

final class OverlappedPool {
  private final ObjectPool<OVERLAPPED_WITH_BUFFER_AND_STATE> pool;
  private final PinnableStruct.IPinListener<OVERLAPPED_WITH_BUFFER_AND_STATE> pin_listener;

  public OverlappedPool(int initialPoolSize) {
    this.pin_listener = new PinnableStruct.IPinListener<OVERLAPPED_WITH_BUFFER_AND_STATE>() {
      @Override
      public boolean unpinned(OVERLAPPED_WITH_BUFFER_AND_STATE instance) {
        pool.returnToPool(instance);
        return false;
      }
    };

    this.pool = new ObjectPool<OVERLAPPED_WITH_BUFFER_AND_STATE>(initialPoolSize, ObjectPool.INFINITE_POOL_SIZE, new ObjectPool.Allocator<OVERLAPPED_WITH_BUFFER_AND_STATE>() {
      @Override
      public OVERLAPPED_WITH_BUFFER_AND_STATE allocateInstance() {
        return OVERLAPPED_WITH_BUFFER_AND_STATE.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE(), pin_listener);
      }

      @Override
      public void disposeInstance(OVERLAPPED_WITH_BUFFER_AND_STATE instance) {
        OVERLAPPED_WITH_BUFFER_AND_STATE.dispose(instance);
      }
    });
  }

  public void dispose() {
    pool.dispose();
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance() {
    return pool.requestInstance();
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance(final int state) {
    return requestInstance(state, null, 0);
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance(final int state, final Pointer buffer, final int buffer_size) {
    final OVERLAPPED_WITH_BUFFER_AND_STATE instance = requestInstance();
    instance.state = state;
    instance.buffer = buffer;
    instance.bufferSize = buffer_size;
    return instance;
  }
}