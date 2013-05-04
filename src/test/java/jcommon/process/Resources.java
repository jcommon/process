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

import jcommon.core.Arch;
import jcommon.core.OSFamily;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Resources {
  private static Map<String, String> resource_map = new HashMap<String, String>(10, 1.0f);

  public static final String
      STDIN_1            = pathForResource("stdin-1")
    , STDERR_1           = pathForResource("stderr-1")
    , STDOUT_1           = pathForResource("stdout-1")
    , STDERR_ECHO_REPEAT = pathForResource("stderr-echo-repeat")
    , STDOUT_ECHO_REPEAT = pathForResource("stdout-echo-repeat")
    , STDOUT_STDERR_ECHO_REPEAT = pathForResource("stdout-stderr-echo-repeat")

    , ENV_VAR_ECHO = pathForResource("env-var-echo")
  ;

  public static String[] ALL = new String[] {
      STDIN_1
    , STDERR_1
    , STDOUT_1
    , STDERR_ECHO_REPEAT
    , STDOUT_ECHO_REPEAT
  };

  public static boolean loadAllResources() {
    return ALL != null && ALL.length > 0;
  }

  public static URI uriForNativeResource(final String name) {
    String os_family;
    switch(OSFamily.getSystemOSFamily()) {
      case Windows:
        os_family = "win32";
        break;
      case Unix:
        os_family = "unix";
        break;
      default:
        throw new IllegalStateException("Unknown OS family: " + OSFamily.getSystemOSFamily());
    }

    String arch;
    switch(Arch.getSystemArch()) {
      case x86:
        arch = "x86";
        break;
      case x86_64:
        arch = "x86_64";
        break;
      default:
        throw new IllegalStateException("Unknown system architecture: " + Arch.getSystemArch());
    }

    final String start = "/native/" + os_family;
    final String[] attempts = new String[] {
        start + "/" + name
      , start + "/" + name + ".bat"
      , start + "/" + name + ".sh"
      , start + "/" + arch + "/" + name
      , start + "/" + arch + "/" + name + ".bat"
      , start + "/" + arch + "/" + name + ".sh"
      , start + "/" + arch + "/bin/" + name
      , start + "/" + arch + "/bin/" + name + ".exe"
    };

    for(String attempt : attempts) {
      final URL url = Resources.class.getResource(attempt);
      if (url != null) {
        try {
          return url.toURI();
        } catch(Throwable t) {
        }
      }
    }
    return null;
  }

  public static File extract(final URI uri) {
    //Resources file name.
    final String path = uri.getPath();
    final int last_index_of_slash = path.lastIndexOf("/");
    if (last_index_of_slash < 0)
      return null;
    final String name = path.substring(last_index_of_slash + 1);
    final int first_index_of_period = name.indexOf(".");
    final String prefix = first_index_of_period >= 0 ? name.substring(0, first_index_of_period) : "tmp";
    final String suffix = first_index_of_period >= 0 ? name.substring(first_index_of_period + 1) : "tmp";

    final File temp;
    try {
      temp = File.createTempFile(prefix + "-", "." + suffix);
    } catch(Throwable t) {
      return null;
    }

    final byte[] buffer = new byte[4096];
    InputStream read_stream = null;
    OutputStream write_stream = null;
    int read;
    try {
      read_stream = uri.toURL().openStream();
      write_stream = new FileOutputStream(temp);
      while((read = read_stream.read(buffer)) >= 0) {
       write_stream.write(buffer, 0, read);
      }
    } catch(Throwable t) {
      t.printStackTrace();
      return null;
    } finally {
      try { read_stream.close(); } catch(Throwable t2) { }
      try { write_stream.close(); } catch(Throwable t2) { }
    }

    temp.setExecutable(true);
    temp.deleteOnExit();

    return temp;
  }

  public static File extractNativeResource(final String name) {
    return extract(uriForNativeResource(name));
  }

  public static void buildResourceMap(final String name) {
    final File f = extractNativeResource(name);
    final String path = f.getAbsolutePath();
    resource_map.put(name, path);
  }

  public static String pathForResource(final String name) {
    if (!resource_map.containsKey(name)) {
      buildResourceMap(name);
    }
    return resource_map.get(name);
  }
}
