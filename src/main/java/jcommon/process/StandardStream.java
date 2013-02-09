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

/**
 * Enum representing standard available streams.
 */
@SuppressWarnings("unused")
public enum StandardStream {
    StdOut(true, false, true)
  , StdErr(true, false, true)
  , StdIn (false, true, true)

  , Null(true, true, false)
  ;

  private final boolean read_stream;
  private final boolean write_stream;
  private final boolean can_process_data;

  private StandardStream(final boolean read_stream, final boolean write_stream, final boolean can_process_data) {
    this.read_stream = read_stream;
    this.write_stream = write_stream;
    this.can_process_data = can_process_data;
  }

  /**
   * Indicates if this standard stream is readable.
   *
   * @return <code>true</code> if this stream is readable.
   */
  public final boolean isReadStream() {
    return read_stream;
  }

  /**
   * Indicates if this standard stream is writable.
   *
   * @return <code>true</code> if this stream is writable.
   */
  public final boolean isWriteStream() {
    return write_stream;
  }

  /**
   * Indicates if this stream can accept and process data or if it will
   * ignore it or error out upon receiving actual bytes of data.
   *
   * @return <code>true</code> if this tream can process data, <code>false</code>
   *         otherwise.
   */
  public final boolean canProcessData() {
    return can_process_data;
  }

  /**
   * Indicates if this stream can be redirected to the stream provided in the
   * to parameter.
   *
   * @param to The stream to test for redirectability.
   * @return <code>true</code> if this stream can be redirected to the provided
   *         stream.
   */
  public final boolean isRedirectableTo(final StandardStream to) {
    return canBeRedirected(this, to);
  }

  /**
   * Indicates if a stream can be redirected to the stream provided in the
   * to parameter.
   *
   * @param from The stream to test for redirectability from.
   * @param to The stream to test for redirectability to.
   * @return <code>true</code> if the stream can be redirected to the provided
   *         stream.
   */
  public static boolean canBeRedirected(final StandardStream from, final StandardStream to) {
    return (from.isReadStream() && to.isReadStream()) || (from.isWriteStream() && to.isWriteStream());
  }
}
