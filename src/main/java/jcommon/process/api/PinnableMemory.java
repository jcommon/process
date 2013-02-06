package jcommon.process.api;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

import java.util.HashMap;

/**
 * Extends {@link com.sun.jna.Memory} and allows you to pin ({@link #pin()}) memory by holding a reference to it until it is
 * unpinned ({@link #unpin()}).
 */
public class PinnableMemory extends Memory {
  private static final HashMap<Pointer, PinnableMemory> pinned = new HashMap<Pointer, PinnableMemory>(2, 1.0f);
  private static final HashMap<Pointer, IPinListener> listeners = new HashMap<Pointer, IPinListener>(2, 1.0f);
  private static final Object pin_lock = new Object();

  public static interface IPinListener {
    boolean unpinned(PinnableMemory memory);
  }

  public PinnableMemory(long size) {
    super(size);
  }

  /**
   * Publicly-accessible form of {@link com.sun.jna.Memory#dispose()}.
   */
  public void dispose() {
    super.dispose();
  }

  public PinnableMemory pin() {
    return pin(null);
  }

  public PinnableMemory pin(final IPinListener listener) {
    synchronized (pin_lock) {
      pinned.put(this, this);
      if (listener != null) {
        listeners.put(this, listener);
      }
    }
    return this;
  }

  public PinnableMemory unpin() {
    synchronized (pin_lock) {
      pinned.remove(this);
      final IPinListener listener = listeners.remove(this);
      if (listener != null) {
        if (listener.unpinned(this)) {
          dispose();
        }
        //If the listener returns false, then we do not
        //explicitly dispose of the memory -- the user will
        //need to take care of that himself.
      } else {
        dispose();
      }
    }
    return this;
  }

  public static PinnableMemory pin(final long size) {
    return new PinnableMemory(size).pin();
  }

  public static PinnableMemory pin(final long size, final IPinListener listener) {
    return new PinnableMemory(size).pin(listener);
  }

  public static void unpin(Pointer ptr) {
    synchronized (pin_lock) {
      final PinnableMemory mem = pinned.get(ptr);
      if (mem != null) {
        mem.unpin();
      }
    }
  }

  public static void dispose(Pointer ptr) {
    synchronized (pin_lock) {
      final PinnableMemory mem = pinned.get(ptr);
      if (mem != null) {
        mem.dispose();
        return;
      }
      if (ptr instanceof PinnableMemory) {
        ((PinnableMemory)ptr).dispose();
      }
    }
  }
}
