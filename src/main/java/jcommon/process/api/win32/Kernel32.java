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

package jcommon.process.api.win32;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jcommon.process.api.PinnableStruct;

import java.nio.Buffer;
import java.util.List;

import static jcommon.process.api.JNAUtils.fromSeq;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.api.win32.Win32.INVALID_HANDLE_VALUE;

public class Kernel32 implements Win32Library {
  public static final int
      MAX_PATH = 260
    , MAX_COMMAND_LINE_SIZE = 32768 - 2 /* - size of the Unicode terminating null character */
  ;

  @SuppressWarnings("unused")
  public static final long
      CREATE_BREAKAWAY_FROM_JOB        = 0x01000000
    , CREATE_DEFAULT_ERROR_MODE        = 0x04000000
    , CREATE_NEW_CONSOLE               = 0x00000010
    , CREATE_NEW_PROCESS_GROUP         = 0x00000200
    , CREATE_NO_WINDOW                 = 0x08000000
    , CREATE_PROTECTED_PROCESS         = 0x00040000
    , CREATE_PRESERVE_CODE_AUTHZ_LEVEL = 0x02000000
    , CREATE_SEPARATE_WOW_VDM          = 0x00000800
    , CREATE_SHARED_WOW_VDM            = 0x00001000
    , CREATE_SUSPENDED                 = 0x00000004
    , CREATE_UNICODE_ENVIRONMENT       = 0x00000400
    , DEBUG_ONLY_THIS_PROCESS          = 0x00000002
    , DEBUG_PROCESS                    = 0x00000001
    , DETACHED_PROCESS                 = 0x00000008
    , EXTENDED_STARTUPINFO_PRESENT     = 0x00080000
    , INHERIT_PARENT_AFFINITY          = 0x00010000
  ;

  @SuppressWarnings("unused")
  public static final long
      ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000
    , BELOW_NORMAL_PRIORITY_CLASS = 0x00004000
    , HIGH_PRIORITY_CLASS         = 0x00000080
    , IDLE_PRIORITY_CLASS         = 0x00000040
    , NORMAL_PRIORITY_CLASS       = 0x00000020
    , REALTIME_PRIORITY_CLASS     = 0x00000100
  ;

  @SuppressWarnings("unused")
  public static final int
      STARTF_FORCEONFEEDBACK  = 0x00000040
    , STARTF_FORCEOFFFEEDBACK = 0x00000080
    , STARTF_PREVENTPINNING   = 0x00002000
    , STARTF_RUNFULLSCREEN    = 0x00000020
    , STARTF_TITLEISAPPID     = 0x00001000
    , STARTF_TITLEISLINKNAME  = 0x00000800
    , STARTF_USECOUNTCHARS    = 0x00000008
    , STARTF_USEFILLATTRIBUTE = 0x00000010
    , STARTF_USEHOTKEY        = 0x00000200
    , STARTF_USEPOSITION      = 0x00000004
    , STARTF_USESHOWWINDOW    = 0x00000001
    , STARTF_USESIZE          = 0x00000002
    , STARTF_USESTDHANDLES    = 0x00000100
  ;

  @SuppressWarnings("unused")
  public static final int
      SW_FORCEMINIMIZE   = 11
    , SW_HIDE            = 0
    , SW_MAXIMIZE        = 3
    , SW_MINIMIZE        = 6
    , SW_RESTORE         = 9
    , SW_SHOW            = 5
    , SW_SHOWDEFAULT     = 10
    , SW_SHOWMAXIMIZED   = 3
    , SW_SHOWMINIMIZED   = 2
    , SW_SHOWMINNOACTIVE = 7
    , SW_SHOWNA          = 8
    , SW_SHOWNOACTIVATE  = 4
    , SW_SHOWNORMAL      = 1
  ;

  @SuppressWarnings("unused")
  public static final int
      HANDLE_FLAG_INHERIT            = 0x00000001
    , HANDLE_FLAG_PROTECT_FROM_CLOSE = 0x00000002
  ;

  /* The following are masks for the predefined standard access types */
  @SuppressWarnings("unused")
  public static final int
      DELETE       = 0x00010000
    , READ_CONTROL = 0x00020000
    , WRITE_DAC    = 0x00040000
    , WRITE_OWNER  = 0x00080000
    , SYNCHRONIZE  = 0x00100000
  ;

  @SuppressWarnings("unused")
  public static final int
      STANDARD_RIGHTS_REQUIRED = 0x000F0000
    , STANDARD_RIGHTS_READ     = READ_CONTROL
    , STANDARD_RIGHTS_WRITE    = READ_CONTROL
    , STANDARD_RIGHTS_EXECUTE  = READ_CONTROL
    , STANDARD_RIGHTS_ALL      = 0x001F0000
    , SPECIFIC_RIGHTS_ALL      = 0x0000FFFF
  ;

  /* File access rights */
  @SuppressWarnings("unused")
  public static final int
      FILE_READ_DATA            = 0x00000001
    , FILE_LIST_DIRECTORY       = 0x00000001
    , FILE_WRITE_DATA           = 0x00000002
    , FILE_ADD_FILE             = 0x00000002
    , FILE_APPEND_DATA          = 0x00000004
    , FILE_ADD_SUBDIRECTORY     = 0x00000004
    , FILE_CREATE_PIPE_INSTANCE = 0x00000004
    , FILE_READ_EA              = 0x00000008
    , FILE_WRITE_EA             = 0x00000010
    , FILE_EXECUTE              = 0x00000020
    , FILE_TRAVERSE             = 0x00000020
    , FILE_DELETE_CHILD         = 0x00000040
    , FILE_READ_ATTRIBUTES      = 0x00000080
    , FILE_WRITE_ATTRIBUTES     = 0x00000100
  ;

  @SuppressWarnings("unused")
  public static final int
      FILE_ALL_ACCESS      = STANDARD_RIGHTS_REQUIRED | SYNCHRONIZE | 0x000001FF
    , FILE_GENERIC_READ    = STANDARD_RIGHTS_READ | SYNCHRONIZE | FILE_READ_DATA | FILE_READ_ATTRIBUTES | FILE_READ_EA
    , FILE_GENERIC_WRITE   = STANDARD_RIGHTS_WRITE | SYNCHRONIZE | FILE_WRITE_DATA | FILE_WRITE_ATTRIBUTES | FILE_WRITE_EA | FILE_APPEND_DATA
    , FILE_GENERIC_EXECUTE = STANDARD_RIGHTS_EXECUTE | SYNCHRONIZE | FILE_READ_ATTRIBUTES | FILE_EXECUTE
  ;


  @SuppressWarnings("unused")
  public static final int
      CREATE_NEW        = 1
    , CREATE_ALWAYS     = 2
    , OPEN_EXISTING     = 3
    , OPEN_ALWAYS       = 4
    , TRUNCATE_EXISTING = 5
  ;

  @SuppressWarnings("unused")
  public static final int
      FILE_FLAG_WRITE_THROUGH      = 0x80000000
    , FILE_FLAG_OVERLAPPED         = 0x40000000
    , FILE_FLAG_NO_BUFFERING       = 0x20000000
    , FILE_FLAG_RANDOM_ACCESS      = 0x10000000
    , FILE_FLAG_SEQUENTIAL_SCAN    = 0x08000000
    , FILE_FLAG_DELETE_ON_CLOSE    = 0x04000000
    , FILE_FLAG_BACKUP_SEMANTICS   = 0x02000000
    , FILE_FLAG_POSIX_SEMANTICS    = 0x01000000
    , FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000
    , FILE_FLAG_OPEN_NO_RECALL     = 0x00100000
  ;

  @SuppressWarnings("unused")
  public static final int
      FILE_ATTRIBUTE_NORMAL = 0x00000080
  ;

  @SuppressWarnings("unused")
  public static final int
      GENERIC_READ    = 0x80000000
    , GENERIC_WRITE   = 0x40000000
    , GENERIC_EXECUTE = 0x20000000
    , GENERIC_ALL     = 0x10000000
  ;

  @SuppressWarnings("unused")
  public static final int
      ERROR_SUCCESS             = 0
    , ERROR_NOT_FOUND           = 0x490
    , ERROR_INSUFFICIENT_BUFFER = 0x7A

    , ERROR_IO_INCOMPLETE       = 996
    , ERROR_IO_PENDING          = 997
    , ERROR_BROKEN_PIPE         = 109
    , ERROR_PIPE_CONNECTED      = 535
    , ERROR_PROC_NOT_FOUND      = 127
    , ERROR_MORE_DATA           = 234
    , ERROR_NO_DATA             = 232
    , ERROR_PIPE_LISTENING      = 536
    , ERROR_FILE_NOT_FOUND      = 2
    , ERROR_ABANDONED_WAIT_0    = 735
    , ERROR_OPERATION_ABORTED   = 995
    , ERROR_INVALID_USER_BUFFER = 0x6F8
    , ERROR_NOT_ENOUGH_MEMORY   = 0x8
    , ERROR_ACCESS_DENIED       = 5
    , ERROR_INVALID_HANDLE      = 6
    , ERROR_HANDLE_EOF          = 38;
  ;

  @SuppressWarnings("unused")
  public static final int
      STILL_ACTIVE = 259
  ;

  @SuppressWarnings("unused")
  public static final int
      DUPLICATE_SAME_ACCESS = 0x02
  ;

  @SuppressWarnings("unused")
  public static final int
      STATUS_WAIT_0             = 0x00000000
    , STATUS_ABANDONED_WAIT_0   = 0x00000080
  ;

  @SuppressWarnings("unused")
  public static final int
      WAIT_ABANDONED_0          = (STATUS_ABANDONED_WAIT_0) + 0
    , WAIT_OBJECT_0             = ((STATUS_WAIT_0) + 0)
    , WAIT_FAILED               = 0xFFFFFFFF
    , WAIT_TIMEOUT              = 258
  ;

  @SuppressWarnings("unused")
  public static final int
      INFINITE                  = 0xFFFFFFFF
  ;

  @SuppressWarnings("unused")
  public static final DWORD
      STD_OUTPUT_HANDLE = new DWORD(-11)
  ;

  /* Define the dwOpenMode values for CreateNamedPipe */
  @SuppressWarnings("unused")
  public static final int
      PIPE_ACCESS_INBOUND  = 0x00000001
    , PIPE_ACCESS_OUTBOUND = 0x00000002
    , PIPE_ACCESS_DUPLEX   = 0x00000003
  ;

  /* Define the Named Pipe End flags for GetNamedPipeInfo  */
  @SuppressWarnings("unused")
  public static final int
      PIPE_CLIENT_END = 0x00000000
    , PIPE_SERVER_END = 0x00000001
  ;

  /* Define the dwPipeMode values for CreateNamedPipe */
  @SuppressWarnings("unused")
  public static final int
      PIPE_WAIT                  = 0x00000000
    , PIPE_NOWAIT                = 0x00000001
    , PIPE_READMODE_BYTE         = 0x00000000
    , PIPE_READMODE_MESSAGE      = 0x00000002
    , PIPE_TYPE_BYTE             = 0x00000000
    , PIPE_TYPE_MESSAGE          = 0x00000004
    , PIPE_ACCEPT_REMOTE_CLIENTS = 0x00000000
    , PIPE_REJECT_REMOTE_CLIENTS = 0x00000008
  ;

  /* Define the well known values for CreateNamedPipe nMaxInstances */
  @SuppressWarnings("unused")
  public static final int
      PIPE_UNLIMITED_INSTANCES = 255
  ;

  public static native int GetLastError();

  public static native HANDLE GetCurrentProcess();
  public static native int GetCurrentProcessId();

  public static HANDLE CreateUnassociatedIoCompletionPort(int concurrencyValue) { return CreateIoCompletionPort(INVALID_HANDLE_VALUE, null, null, concurrencyValue); }
  public static boolean AssociateHandleWithIoCompletionPort(HANDLE IOComPortHandle, HANDLE Associate, Pointer CompletionKey) { return IOComPortHandle.equals(CreateIoCompletionPort(Associate, IOComPortHandle, CompletionKey, 0)); }

  public static native HANDLE  /*HANDLE*/ CreateIoCompletionPort(HANDLE /*HANDLE*/ fileHandle, HANDLE /*HANDLE*/ existingCompletionPort, Pointer /*ULONG_PTR*/ completionKey, int /*DWORD*/ numberOfConcurrentThreads);
  public static native boolean /*BOOL*/   GetQueuedCompletionStatus(HANDLE /*HANDLE*/ completionPort, IntByReference /*LPDWORD*/ lpNumberOfBytes, PointerByReference /*PULONG_PTR*/ lpCompletionKey, PointerByReference /*LPOVERLAPPED*/ lpOverlapped, int /*DWORD*/ dwMilliseconds);
  public static native boolean /*BOOL*/   GetQueuedCompletionStatus(HANDLE /*HANDLE*/ completionPort, IntByReference /*LPDWORD*/ lpNumberOfBytes, IntByReference /*PULONG_PTR*/ lpCompletionKey, PointerByReference /*LPOVERLAPPED*/ lpOverlapped, int /*DWORD*/ dwMilliseconds);
  public static native boolean /*BOOL*/   PostQueuedCompletionStatus(HANDLE /*HANDLE*/ completionPort, int /*DWORD*/ dwNumberOfBytesTransferred, Pointer /*ULONG_PTR*/ dwCompletionKey, OVERLAPPED /*LPOVERLAPPED*/ lpOverlapped);
  public static native boolean /*BOOL*/   PostQueuedCompletionStatus(HANDLE /*HANDLE*/ completionPort, int /*DWORD*/ dwNumberOfBytesTransferred, int /*ULONG_PTR*/ dwCompletionKey, OVERLAPPED /*LPOVERLAPPED*/ lpOverlapped);
  public static native boolean /*BOOL*/   FlushFileBuffers(HANDLE hFile);
  public static native boolean /*BOOL*/   GetOverlappedResult(HANDLE hFile, Pointer /*OVERLAPPED*/ lpOverlapped, IntByReference lpNumberOfBytesTransferred, boolean bWait);


  public static HANDLE CreateEvent(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, String name) { return CharEncodingSpecific.CreateEvent(security, manual, initial, name); }
  public static native int     /*DWORD*/  WaitForSingleObject(HANDLE /*HANDLE*/ hHandle, int /*DWORD*/ dwMilliseconds);
  public static native boolean /*BOOL*/   SetEvent(HANDLE /*HANDLE*/ hEvent);

  public static native boolean CreatePipe(HANDLEByReference hReadPipe, HANDLEByReference hWritePipe, SECURITY_ATTRIBUTES lpPipeAttributes, int nSize);
  public static HANDLE CreateNamedPipe(String lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes) { return CharEncodingSpecific.CreateNamedPipe(lpName, dwOpenMode, dwPipeMode, nMaxInstances, nOutBufferSize, nInBufferSize, nDefaultTimeOut, lpSecurityAttributes); }
  public static native boolean ConnectNamedPipe(HANDLE hNamedPipe, OVERLAPPED lpOverlapped);

  public static native boolean SetHandleInformation(HANDLE hObject, int dwMask, int dwFlags);

  public static HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile) { return CharEncodingSpecific.CreateFile(lpFileName, dwDesiredAccess, dwShareMode, lpSecurityAttributes, dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile); }
  public static native boolean ReadFile(HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToRead, IntByReference lpNumberOfBytesRead, OVERLAPPED lpOverlapped);
  public static native boolean ReadFile(HANDLE hFile, Buffer lpBuffer, int nNumberOfBytesToRead, IntByReference lpNumberOfBytesRead, OVERLAPPED lpOverlapped);
  public static native boolean WriteFile(HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToWrite, IntByReference lpNumberOfBytesWritten, OVERLAPPED lpOverlapped);
  public static native boolean WriteFile(HANDLE hFile, Buffer lpBuffer, int nNumberOfBytesToWrite, IntByReference lpNumberOfBytesWritten, OVERLAPPED lpOverlapped);

  public static native boolean DuplicateHandle(HANDLE hSourceProcessHandle, HANDLE hSourceHandle, HANDLE hTargetProcessHandle, HANDLEByReference lpTargetHandle, int dwDesiredAccess, boolean bInheritHandle, int dwOptions);
  public static native int CloseHandle(HANDLE hObject);
  public static native HANDLE GetStdHandle(DWORD nStdHandle);
  public static native int ResumeThread(HANDLE hThread);
  public static native int GetExitCodeProcess(HANDLE hProcess, IntByReference lpExitCode);
  public static int CreateProcess(String lpApplicationName, String lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation) { return CharEncodingSpecific.CreateProcess(lpApplicationName, lpCommandLine, lpProcessAttributes, lpThreadAttributes, bInheritHandles, dwCreationFlags, lpEnvironment, lpCurrentDirectory, lpStartupInfo, lpProcessInformation); }

  private static final String LIB = "Kernel32";

  static {
    Native.register(LIB);
    CharEncodingSpecific = USE_UNICODE ? new Unicode() : new ASCII();
    //CharEncodingSpecific = new ASCII();
  }

  static CharacterEncodingSpecificFunctions CharEncodingSpecific;

  public static interface CharacterEncodingSpecificFunctions {
    HANDLE CreateEvent(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, String name);
    int SetCurrentDirectory(final String path);
    HANDLE CreateNamedPipe(String lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes);
    HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile);
    int CreateProcess(String lpApplicationName, String lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation);
  }

  public static class ASCII implements CharacterEncodingSpecificFunctions {
    static {
      Native.register(LIB);
    }

    public static native HANDLE CreateEventA(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, String name);
    @Override
    public HANDLE CreateEvent(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, String name) { return CreateEventA(security, manual, initial, name); }

    public static native int SetCurrentDirectoryA(final String path);
    @Override
    public int SetCurrentDirectory(final String path) { return SetCurrentDirectoryA(path); }

    public static native HANDLE CreateNamedPipeA(String lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes);
    @Override
    public HANDLE CreateNamedPipe(String lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes) { return CreateNamedPipeA(lpName, dwOpenMode, dwPipeMode, nMaxInstances, nOutBufferSize, nInBufferSize, nDefaultTimeOut, lpSecurityAttributes); }

    public static native HANDLE CreateFileA(String lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile);
    public HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile) { return CreateFileA(lpFileName, dwDesiredAccess, dwShareMode, lpSecurityAttributes, dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile); }

    public static native int CreateProcessA(String lpApplicationName, String lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation) throws LastErrorException;
    @Override
    public int CreateProcess(String lpApplicationName, String lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation) { return CreateProcessA(lpApplicationName, lpCommandLine, lpProcessAttributes, lpThreadAttributes, bInheritHandles, dwCreationFlags, lpEnvironment, lpCurrentDirectory, lpStartupInfo, lpProcessInformation); }
  }

  public static class Unicode implements CharacterEncodingSpecificFunctions {
    static {
      Native.register(LIB);
    }

    public static native HANDLE CreateEventW(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, WString name);
    @Override
    public HANDLE CreateEvent(SECURITY_ATTRIBUTES security, boolean manual, boolean initial, String name) { return CreateEventW(security, manual, initial, name != null ? new WString(name) : null); }

    public static native int SetCurrentDirectoryW(final String path);
    @Override
    public int SetCurrentDirectory(final String path) { return SetCurrentDirectoryW(path); }

    public static native HANDLE CreateNamedPipeW(WString lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes);
    @Override
    public HANDLE CreateNamedPipe(String lpName, int dwOpenMode, int dwPipeMode, int nMaxInstances, int nOutBufferSize, int nInBufferSize, int nDefaultTimeOut, SECURITY_ATTRIBUTES lpSecurityAttributes) { return CreateNamedPipeW(new WString(lpName), dwOpenMode, dwPipeMode, nMaxInstances, nOutBufferSize, nInBufferSize, nDefaultTimeOut, lpSecurityAttributes); }

    public static native HANDLE CreateFileW(WString lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile);
    public HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile) { return CreateFileW(new WString(lpFileName), dwDesiredAccess, dwShareMode, lpSecurityAttributes, dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile); }

    public static native int CreateProcessW(WString lpApplicationName, WString lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation) throws LastErrorException;
    @Override
    public int CreateProcess(String lpApplicationName, String lpCommandLine, SECURITY_ATTRIBUTES lpProcessAttributes, SECURITY_ATTRIBUTES lpThreadAttributes, boolean bInheritHandles, DWORD dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo, PROCESS_INFORMATION.ByReference lpProcessInformation) { return CreateProcessW(lpApplicationName == null ? null : new WString(lpApplicationName), lpCommandLine != null ? new WString(lpCommandLine) : null, lpProcessAttributes, lpThreadAttributes, bInheritHandles, dwCreationFlags, lpEnvironment, lpCurrentDirectory, lpStartupInfo, lpProcessInformation); }
  }

  public static class OVERLAPPED extends PinnableStruct<OVERLAPPED> {
    public ULONG_PTR Internal;
    public ULONG_PTR InternalHigh;
    public int Offset;
    public int OffsetHigh;
    public HANDLE hEvent;

    private static final List FIELD_ORDER = fromSeq(
        "Internal"
      , "InternalHigh"
      , "Offset"
      , "OffsetHigh"
      , "hEvent"
    );

    @Override
    protected List getFieldOrder() {
      return FIELD_ORDER;
    }
  }

  public static class SECURITY_ATTRIBUTES extends Structure {
    public DWORD dwLength;
    public Pointer lpSecurityDescriptor;
    public boolean bInheritHandle;

    private static final List FIELD_ORDER = fromSeq(
        "dwLength"
      , "lpSecurityDescriptor"
      , "bInheritHandle"
    );

    public SECURITY_ATTRIBUTES() {
      dwLength = new DWORD(size());
    }

    @Override
    protected List getFieldOrder() {
      return FIELD_ORDER;
    }
  }

  public static class PROCESS_INFORMATION extends Structure {
    public HANDLE hProcess;
    public HANDLE hThread;
    public DWORD dwProcessId;
    public DWORD dwThreadId;

    private static final List FIELD_ORDER = fromSeq(
        "hProcess"
      , "hThread"
      , "dwProcessId"
      , "dwThreadId"
    );

    public static class ByReference extends PROCESS_INFORMATION implements Structure.ByReference {
        public ByReference() {
        }

        public ByReference(Pointer memory) {
            super(memory);
        }
    }

    public PROCESS_INFORMATION() {
    }

    public PROCESS_INFORMATION(Pointer memory) {
        super(memory);
        read();
    }

    @Override
    protected List getFieldOrder() {
      return FIELD_ORDER;
    }
  }

  public static class STARTUPINFO extends Structure {
    public DWORD cb;
    public String lpReserved;
    public String lpDesktop;
    public String lpTitle;
    public DWORD dwX;
    public DWORD dwY;
    public DWORD dwXSize;
    public DWORD dwYSize;
    public DWORD dwXCountChars;
    public DWORD dwYCountChars;
    public DWORD dwFillAttribute;
    public int dwFlags;
    public WORD wShowWindow;
    public WORD cbReserved2;
    public Pointer lpReserved2;
    public HANDLE hStdInput;
    public HANDLE hStdOutput;
    public HANDLE hStdError;

    private static final List FIELD_ORDER = fromSeq(
        "cb"
      , "lpReserved"
      , "lpDesktop"
      , "lpTitle"
      , "dwX"
      , "dwY"
      , "dwXSize"
      , "dwYSize"
      , "dwXCountChars"
      , "dwYCountChars"
      , "dwFillAttribute"
      , "dwFlags"
      , "wShowWindow"
      , "cbReserved2"
      , "lpReserved2"
      , "hStdInput"
      , "hStdOutput"
      , "hStdError"
    );

    public STARTUPINFO() {
      cb = new DWORD(size());
    }

    @Override
    protected List getFieldOrder() {
      return FIELD_ORDER;
    }
  }
}
