package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.api.ObjectPool;
import jcommon.process.api.PinnableStruct;

final class OverlappedPool {
  private final ObjectPool<Pointer> pool;
  private final PinnableStruct.IPinListener pin_listener;

  public OverlappedPool(int initialPoolSize) {
    this.pin_listener = new PinnableStruct.IPinListener() {
      @Override
      public void unpinned(Pointer instance) {
        pool.returnToPool(instance);
      }
    };

    this.pool = new ObjectPool<Pointer>(initialPoolSize, ObjectPool.INFINITE_POOL_SIZE, new ObjectPool.Allocator<Pointer>() {
      @Override
      public Pointer allocateInstance() {
        return PinnableStruct.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE(), pin_listener).getPointer();
      }

      @Override
      public void disposeInstance(Pointer instance) {
      }
    });
  }

  public void dispose() {
    pool.dispose();
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance() {
    //synchronized (pool.getLock()) {
      return new OVERLAPPED_WITH_BUFFER_AND_STATE(pool.requestInstance());
    //}
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance(final int state) {
    return requestInstance(state, null, 0);
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE requestInstance(final int state, final Pointer buffer, final int buffer_size) {
    //synchronized (pool.getLock()) {
      final OVERLAPPED_WITH_BUFFER_AND_STATE instance = new OVERLAPPED_WITH_BUFFER_AND_STATE(pool.requestInstance());
      instance.state = state;
//      instance.iocpBuffer = new IOCPBUFFER();
//      instance.iocpBuffer.buffer = buffer;
//      instance.iocpBuffer.bufferSize = buffer_size;
      return instance;
    //}
  }
}