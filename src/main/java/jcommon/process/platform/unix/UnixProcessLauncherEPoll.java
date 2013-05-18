package jcommon.process.platform.unix;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableObject;
import jcommon.process.api.PinnableStruct;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnixProcessLauncherEPoll {
  public static IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariable[] environment_variables, final String[] args, final IProcessListener[] listeners) {
    //http://stackoverflow.com/questions/6606870/suspend-forked-process-at-startup
    //call to sigsuspend(2), followed by a kill(pid, SIGXXX) from parent, where SIGXXX is the signal of choice. SIGCONT maybe?
    return null;
  }
}
