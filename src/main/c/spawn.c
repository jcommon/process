#include <stdlib.h>
#include <limits.h>
#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <semaphore.h>
#include <stdbool.h>

#define DEBUG(format, ...)                                                          \
  {                                                                                 \
    fprintf(stderr, format "\n", ## __VA_ARGS__);                                   \
    fflush(stderr);                                                                 \
  }

#define INFO(format, ...)                                                           \
  {                                                                                 \
    fprintf(stdout, format "\n", ## __VA_ARGS__);                                   \
    fflush(stdout);                                                                 \
  }

#define ERROR(format, ...)                                                          \
  {                                                                                 \
    fprintf(stderr, format "\n", ## __VA_ARGS__);                                   \
    fflush(stderr);                                                                 \
  }

#define EXIT_ERROR -1
#define EXIT_SUCCESS 0

#define BUFFER_SIZE 1024

#define SIGNAL_TO_RESUME SIGINT
#define HANDLED_SIGNALS_SIZE 1
static int HANDLED_SIGNALS[] = {
  SIGINT
};

static sem_t sem;
static volatile bool signal_received = false;

static void usage() {
  INFO("Usage: ");
  INFO("  spawn <read pipe fd> <write pipe fd>");
}

static bool parse_to_int(const char* value, int* ret) {
  long val;
  char *endptr;

  errno = 0;
  val = strtol(value, &endptr, 10);
  if ((errno == ERANGE && (val == LONG_MAX || val == LONG_MIN)) || (errno != 0 && val == 0)) {
    return false;
  }

  *ret = (int)val;
  return true;
}

static void sighandler(int signum, siginfo_t *info, void *ptr) {
  DEBUG("Received signal from %d: %d\n", info->si_pid, signum);
  //if (info->si_pid != getppid()) {
  //  return;
  //}

  if (signum != SIGNAL_TO_RESUME) {
    return;
  }

  signal_received = true;
  sem_post(&sem);
}

int main(int argc, const char *argv[]) {
  //This process begins effectively suspended until another process
  //(which should be the parent process) sends a SIGINT signal to
  //instruct this process to continue execution.
  //
  //Upon continuing execution, it calls execve() in order to replace
  //itself with the intended process.
  //
  //Where is the fork? The parent Java process is calling posix_spawn()
  //to launch this process, so it should have effectively already
  //forked (vforked if possible).

  int read_fd, write_fd;
  struct sigaction act;
  int s, i, ret;
  ssize_t bytes_read, bytes_written;
  char buffer[BUFFER_SIZE];

  //setvbuf(stdout, NULL, _IONBF, 0);

  for(i = 0; i < argc; ++i) {
    ERROR("%d: %s", i, argv[i]);
  }

  if (argc != 3) {
    ERROR("Invalid arguments.");
    usage();
    return EXIT_ERROR;
  }

  if (!parse_to_int(argv[1], &read_fd)) {
    ERROR("Invalid <read fd>.")
    usage();
    return EXIT_ERROR;
  }

  if (!parse_to_int(argv[2], &write_fd)) {
    ERROR("Invalid <write fd>.")
    usage();
    return EXIT_ERROR;
  }

  INFO("READ FD: %d", read_fd);
  INFO("WRITE FD: %d", write_fd);

  bytes_read = read(read_fd, buffer, BUFFER_SIZE);

  INFO("RECVD: %s (%d bytes)", buffer, bytes_read);


//
//  //Clear the buffer.
//  memset (buffer, 0, BUFFER_SIZE);
//
//  //Still have an issue if the signal is sent before we have the chance
//  //to register our signal handler.
//
//  sem_init(&sem, 0, 0);
//
//  act.sa_sigaction = sighandler;
//  act.sa_flags = SA_SIGINFO;
//  sigemptyset(&act.sa_mask);
//
//  for(i = 0; i < HANDLED_SIGNALS_SIZE; ++i) {
//    sigaction(HANDLED_SIGNALS[i], &act, NULL);
//  }
//
//  while(((s = sem_wait(&sem)) != 0 || (s != -1 && errno != EINTR)) && !signal_received) {
//    ;
//  }
//
//  sem_destroy(&sem);
//
//  DEBUG("%s\n", "AFTER 1");
//
//  chdir("/work/etc/jcommon/process/src/main");
//
//  DEBUG("%s\n", "AFTER 2");
//
//  //Replace the current process w/ the desired real work.
//
//  char *temp[] = { NULL, ".", NULL };
//  temp[0] = "ls";
//  ret = __execvpe("ls", temp, NULL);
//
//  //Should only happen if execve() fails.
//  return ret;
}

//static int HANDLED_SIGNALS[] = {
//    SIGHUP    /* Hangup (POSIX).  */
//  , SIGINT		/* Interrupt (ANSI).  */
//  , SIGQUIT		/* Quit (POSIX).  */
//  , SIGILL		/* Illegal instruction (ANSI).  */
//  , SIGTRAP		/* Trace trap (POSIX).  */
//  , SIGABRT		/* Abort (ANSI).  */
//  , SIGIOT		/* IOT trap (4.2 BSD).  */
//  , SIGBUS		/* BUS error (4.2 BSD).  */
//  , SIGFPE		/* Floating-point exception (ANSI).  */
//  //, SIGKILL		/* Kill, unblockable (POSIX).  */
//  , SIGUSR1		/* User-defined signal 1 (POSIX).  */
//  , SIGSEGV		/* Segmentation violation (ANSI).  */
//  , SIGUSR2		/* User-defined signal 2 (POSIX).  */
//  , SIGPIPE		/* Broken pipe (POSIX).  */
//  , SIGALRM		/* Alarm clock (POSIX).  */
//  , SIGTERM		/* Termination (ANSI).  */
//  , SIGSTKFLT	/* Stack fault.  */
//  , SIGCHLD		/* Child status has changed (POSIX).  */
//  , SIGCONT		/* Continue (POSIX).  */
//  //, SIGSTOP		/* Stop, unblockable (POSIX).  */
//  , SIGTSTP		/* Keyboard stop (POSIX).  */
//  , SIGTTIN		/* Background read from tty (POSIX).  */
//  , SIGTTOU		/* Background write to tty (POSIX).  */
//  , SIGURG		/* Urgent condition on socket (4.2 BSD).  */
//  , SIGXCPU		/* CPU limit exceeded (4.2 BSD).  */
//  , SIGXFSZ		/* File size limit exceeded (4.2 BSD).  */
//  , SIGVTALRM	/* Virtual alarm clock (4.2 BSD).  */
//  , SIGPROF		/* Profiling alarm clock (4.2 BSD).  */
//  , SIGWINCH	/* Window size change (4.3 BSD, Sun).  */
//  , SIGPOLL		/* Pollable event occurred (System V).  */
//  , SIGIO		  /* I/O now possible (4.2 BSD).  */
//  , SIGPWR		/* Power failure restart (System V).  */
//  , SIGSYS		/* Bad system call.  */
//  //, SIGUNUSED
//};