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

package jcommon.core.concurrent;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

import static jcommon.core.concurrent.BoundedAutoGrowThreadPool.*;

@SuppressWarnings("unchecked")
public class BoundedAutoGrowThreadPoolTest {
  private static class CounterCallback implements IGrowCallback<Object>, IShrinkCallback<Object> {
    private final AtomicInteger counter = new AtomicInteger(0);

    public int getCounter() {
      return counter.get();
    }

    @Override
    public IWorker<Object> growNewWorker(Object value) {
      counter.incrementAndGet();
      return null;
    }

    @Override
    public void shrink(Object value, Thread thread, IWorker<Object> worker) {
      counter.decrementAndGet();
    }
  }

  private static final IGrowCallback<Object> RETURN_NULL_GROW_CALLBACK = new IGrowCallback<Object>() {
    @Override
    public IWorker<Object> growNewWorker(Object value) {
      return null;
    }
  };

  private static final IShrinkCallback<Object> DO_NOTHING_SHRINK_CALLBACK = new IShrinkCallback<Object>() {
    @Override
    public void shrink(Object value, Thread thread, IWorker<Object> worker) {
      //Do nothing.
    }
  };

  @BeforeClass
  public static void before() {
  }

  @Test(expected = NullPointerException.class)
  public void nullCallbacks() {
    BoundedAutoGrowThreadPool.create(0, 0, null, null, null);
  }

  @Test(expected = NullPointerException.class)
  public void nullGrowCallback() {
    BoundedAutoGrowThreadPool.create(0, 0, null, null, DO_NOTHING_SHRINK_CALLBACK);
  }

  @Test(expected = NullPointerException.class)
  public void nullShrinkCallback() {
    BoundedAutoGrowThreadPool.create(0, 0, null, RETURN_NULL_GROW_CALLBACK, null);
  }

  @Test
  public void nullShutdownCallback() {
    new BoundedAutoGrowThreadPool(0, 0, null, RETURN_NULL_GROW_CALLBACK, DO_NOTHING_SHRINK_CALLBACK, null).stopAll();
  }

  @Test
  public void growWithZeroSizedPool() throws Throwable {
    final BoundedAutoGrowThreadPool pool = BoundedAutoGrowThreadPool.create(0, 0, RETURN_NULL_GROW_CALLBACK, DO_NOTHING_SHRINK_CALLBACK);
    try {
      assertEquals(0, pool.getPoolSize());
      assertEquals(0, pool.getMinimumPoolSize());
      assertEquals(0, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());

      pool.grow();
      assertEquals(0, pool.getPoolSize());
      assertEquals(0, pool.getMinimumPoolSize());
      assertEquals(0, pool.getMaximumPoolSize());
      assertEquals(1, pool.getCoreSize());

      pool.shrink();
      assertEquals(0, pool.getPoolSize());
      assertEquals(0, pool.getMinimumPoolSize());
      assertEquals(0, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());
    } finally {
      pool.stopAll();
    }
  }

  @Test
  public void growWithFixedSizedPool() throws Throwable {
    final BoundedAutoGrowThreadPool pool = BoundedAutoGrowThreadPool.create(2, 2, RETURN_NULL_GROW_CALLBACK, DO_NOTHING_SHRINK_CALLBACK);
    try {
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(2, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());

      pool.grow();
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(2, pool.getMaximumPoolSize());
      assertEquals(1, pool.getCoreSize());

      pool.shrink();
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(2, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());

      //Attempt to go below 0. Nothing should change.
      pool.shrink();
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(2, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());
    } finally {
      //Stopping should cause the pool size to actually reach 0.
      pool.stopAll();
      assertEquals(0, pool.getPoolSize());
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(2, pool.getMaximumPoolSize());
      assertEquals(0, pool.getCoreSize());
    }
  }

  @Test
  public void testGrowingAndShrinking() throws Throwable {
    final CounterCallback callback = new CounterCallback();
    assertEquals(0, callback.getCounter());

    final BoundedAutoGrowThreadPool pool = BoundedAutoGrowThreadPool.create(2, 6, callback, callback);
    try {
      //Core: 0 (starts out at 0 even though the pool size is different b/c the pool has to be at least as large as the min. which is 2)
      //Pool: 2 (starts out at the min.)
      //Counter: 2 (starts out at the min. b/c 2 grows are initiated)
      assertEquals(0, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 0 + 2
      //Pool: 2 (doesn't grow b/c it was already at its min. and the core hasn't grown beyond it)
      //Counter: 2 (should always be the same as the pool)
      pool.growBy(2);
      assertEquals(2, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 1 (decrements by 1)
      //Pool: 2 (cannot go below the min., so it stays at 2)
      //Counter: 2 (remains in sync w/ the pool size)
      pool.shrink();
      assertEquals(1, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 3 (increments by 2)
      //Pool: 3 (goes up by 1 b/c it was already at the min. of 2 and to match core, it needs to go up by just 1)
      //Counter: 3 (remains in sync w/ the pool size)
      pool.growBy(2);
      assertEquals(3, pool.getCoreSize());
      assertEquals(3, pool.getPoolSize());
      assertEquals(3, callback.getCounter());

      //Core: 5 (increments by 2)
      //Pool: 5 (goes up by 2 b/c it's still below the max.)
      //Counter: 5 (remains in sync w/ the pool size)
      pool.growBy(2);
      assertEquals(5, pool.getCoreSize());
      assertEquals(5, pool.getPoolSize());
      assertEquals(5, callback.getCounter());

      //Core: 7 (increments by 2)
      //Pool: 6 (goes up by 1 b/c it hit the max.)
      //Counter: 6 (remains in sync w/ the pool size)
      pool.growBy(2);
      assertEquals(7, pool.getCoreSize());
      assertEquals(6, pool.getPoolSize());
      assertEquals(6, callback.getCounter());

      //Core: 12 (increments by 5)
      //Pool: 6 (already at the max.)
      //Counter: 6 (remains in sync w/ the pool size)
      pool.growBy(5);
      assertEquals(12, pool.getCoreSize());
      assertEquals(6, pool.getPoolSize());
      assertEquals(6, callback.getCounter());

      //Core: 10 (decrements by 2)
      //Pool: 6 (already at the max.)
      //Counter: 6 (remains in sync w/ the pool size)
      pool.shrinkBy(2);
      assertEquals(10, pool.getCoreSize());
      assertEquals(6, pool.getPoolSize());
      assertEquals(6, callback.getCounter());

      //Core: 5 (decrements by 5)
      //Pool: 5 (dips below the max. b/c core is now below the max.)
      //Counter: 5 (remains in sync w/ the pool size)
      pool.shrinkBy(5);
      assertEquals(5, pool.getCoreSize());
      assertEquals(5, pool.getPoolSize());
      assertEquals(5, callback.getCounter());

      //Core: 1 (decrements by 4)
      //Pool: 2 (stays at min.)
      //Counter: 2 (remains in sync w/ the pool size)
      pool.shrinkBy(4);
      assertEquals(1, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 0 (decrements by 1)
      //Pool: 2 (stays at min.)
      //Counter: 2 (remains in sync w/ the pool size)
      pool.shrinkBy(1);
      assertEquals(0, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 0 (does not go below 0)
      //Pool: 2 (stays at min.)
      //Counter: 2 (remains in sync w/ the pool size)
      pool.shrinkBy(2);
      assertEquals(0, pool.getCoreSize());
      assertEquals(2, pool.getPoolSize());
      assertEquals(2, callback.getCounter());

      //Core: 10 (increments by 10)
      //Pool: 6 (hits the max.)
      //Counter: 6 (remains in sync w/ the pool size)
      pool.growBy(10);
      assertEquals(10, pool.getCoreSize());
      assertEquals(6, pool.getPoolSize());
      assertEquals(6, callback.getCounter());
    } finally {
      //Stopping shrinks the pool and everything back down to 0.
      pool.stopAll();
      assertEquals(0, pool.getCoreSize());
      assertEquals(0, pool.getPoolSize());
      assertEquals(0, callback.getCounter());

      //Min. and max. sizes should remain the same.
      assertEquals(2, pool.getMinimumPoolSize());
      assertEquals(6, pool.getMaximumPoolSize());
    }
  }
}
