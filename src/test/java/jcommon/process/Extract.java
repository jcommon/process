package jcommon.process;

import jcommon.core.Arch;
import jcommon.core.OSFamily;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class Extract {
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
      , start + "/" + arch + "/" + name
      , start + "/" + arch + "/" + name + ".bat"
      , start + "/" + arch + "/" + name + ".sh"
      , start + "/" + arch + "/bin/" + name
      , start + "/" + arch + "/bin/" + name + ".exe"
    };

    for(String attempt : attempts) {
      final URL url = Extract.class.getResource(attempt);
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
    //Extract file name.
    final String path = uri.getPath();
    final int last_index_of_slash = path.lastIndexOf("/");
    if (last_index_of_slash < 0)
      return null;
    final String name = path.substring(last_index_of_slash + 1);

    System.out.println(name);

    return null;
  }

  public static File extractNativeResource(final String name) {
    return extract(uriForNativeResource(name));
  }

  public static void extractAllResources() {
    extractNativeResource("stderr-1");
  }
}
