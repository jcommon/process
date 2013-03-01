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

package jcommon.process;

import jcommon.process.api.ByteArrayPool;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public abstract class ProcessListener implements IProcessListener {
  private static ByteArrayPool buffer_pool = null;
  private static final Object buffer_pool_lock = new Object();
  private static final AtomicInteger running = new AtomicInteger(0);

  private void init() {
    synchronized (buffer_pool_lock) {
      if (running.getAndIncrement() == 0) {
        buffer_pool = new ByteArrayPool(2, 1024);
      }
    }
  }

  private void check() {
    synchronized (buffer_pool_lock) {
      if (buffer_pool == null) {
        init();
      }
    }
  }

  private void release() {
    synchronized (buffer_pool_lock) {
      if (running.decrementAndGet() == 0) {
        //buffer_pool.dispose();
        //buffer_pool = null;
      }
    }
  }

  @Override
  public final void started(IProcess process) throws Throwable {
    init();

    processStarted(process);
  }

  @Override
  public final void stopped(IProcess process, int exitCode) throws Throwable {
    //Thread.sleep(3000);
    release();

    synchronized (process) {
    processStopped(process, exitCode);
    }
  }

  @Override
  public final void stdout(IProcess process, ByteBuffer buffer, int bytesRead) throws Throwable {
//    check();
//    final ByteArrayPool.Buffer pool_buffer = buffer_pool.requestInstance();
//    try {
//      stdout(process, buffer, bytesRead, pool_buffer.getBuffer(), pool_buffer.getBufferSize());
//    } finally {
//      buffer_pool.returnToPool(pool_buffer);
//    }
    synchronized (process) {
    stdout(process, buffer, bytesRead, new byte[bytesRead], bytesRead);
    }
  }

  @Override
  public final void stderr(IProcess process, ByteBuffer buffer, int bytesRead) throws Throwable {
    final ByteArrayPool.Buffer pool_buffer = buffer_pool.requestInstance();
    try {
      stderr(process, buffer, bytesRead, pool_buffer.getBuffer(), pool_buffer.getBufferSize());
    } finally {
      buffer_pool.returnToPool(pool_buffer);
    }
  }

  @Override
  public final void error(IProcess process, Throwable t) {
    processError(process, t);
  }

  protected void processStarted(IProcess process) throws Throwable { }
  protected void processStopped(IProcess process, int exitCode) throws Throwable { }
  protected void stdout(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable { }
  protected void stderr(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable { }
  protected void processError(IProcess process, Throwable t) { }
}
