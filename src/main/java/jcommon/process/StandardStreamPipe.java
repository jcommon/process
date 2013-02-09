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

import java.io.PrintStream;
import java.nio.ByteBuffer;

/**
 * Redirects child process standard streams such as stdout and stderr
 * through the parent process' equivalent streams.
 */
public class StandardStreamPipe extends ProcessListener {
  private StandardStream stdout_stream = StandardStream.StdOut;
  private StandardStream stderr_stream = StandardStream.StdErr;

  private StandardStreamPipe() {
  }

  public static StandardStreamPipe create() {
    return new StandardStreamPipe();
  }

  public StandardStreamPipe redirectStdOut(final StandardStream stream) {
    if (!StandardStream.StdOut.isRedirectableTo(stream)) {
      throw new IllegalArgumentException("Cannot redirect from " + StandardStream.StdOut.name() + " to " + stream.name());
    }
    this.stdout_stream = stream;
    return this;
  }

  public StandardStreamPipe redirectStdErr(final StandardStream stream) {
    if (!StandardStream.StdErr.isRedirectableTo(stream)) {
      throw new IllegalArgumentException("Cannot redirect from " + StandardStream.StdErr.name() + " to " + stream.name());
    }
    this.stderr_stream = stream;
    return this;
  }

  @Override
  protected void stdout(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) {
    if (!stdout_stream.canProcessData() || !stdout_stream.isReadStream()) {
      return;
    }

    final PrintStream output;
    switch(stdout_stream) {
      case StdOut:
        output = System.out;
        break;
      case StdErr:
        output = System.err;
        break;
      default:
        output = null;
        break;
    }

    if (output == null) {
      return;
    }

    final int limit = Math.min(bytesRead, poolBufferSize);
    int chunk_size;

    while(buffer.hasRemaining()) {
      chunk_size = Math.min(buffer.remaining(), limit);
      buffer.get(availablePoolBuffer, 0, chunk_size);
      output.write(availablePoolBuffer, 0, chunk_size);
    }
  }

  @Override
  protected void stderr(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) {
    if (!stderr_stream.canProcessData() || !stderr_stream.isReadStream()) {
      return;
    }

    final PrintStream output;
    switch(stderr_stream) {
      case StdOut:
        output = System.out;
        break;
      case StdErr:
        output = System.err;
        break;
      default:
        output = null;
        break;
    }

    if (output == null) {
      return;
    }

    final int limit = Math.min(bytesRead, poolBufferSize);
    int chunk_size;

    while(buffer.hasRemaining()) {
      chunk_size = Math.min(buffer.remaining(), limit);
      buffer.get(availablePoolBuffer, 0, chunk_size);
      output.write(availablePoolBuffer, 0, chunk_size);
    }
  }
}
