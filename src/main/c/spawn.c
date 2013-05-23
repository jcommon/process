#include <stdio.h>
#include <signal.h>
#include <semaphore.h>
#include <errno.h>
#include <stdbool.h>
#include <mqueue.h>

#define QUEUE_NAME "/FC94525A-FB42-4BA1-9D8E-A0CA72033751/%d/%d"

#define DEBUG(format, ...)                                                          \
  {                                                                                 \
    fprintf(stderr, format "\n", ## __VA_ARGS__);                                   \
    fflush(stderr);                                                                 \
  }

#define ERROR -1
#define SUCCESS 0

#define BUFFER_SIZE 1024

#define SIGNAL_TO_RESUME SIGINT
#define HANDLED_SIGNALS_SIZE 1
static int HANDLED_SIGNALS[] = {
  SIGINT
};

static sem_t sem;
static volatile bool signal_received = false;

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

  char queue_name[128];
  struct sigaction act;
  int s, i, queue_name_size, ret;
  mqd_t queue_handle;
  struct mq_attr msgq_attr;
  ssize_t bytes_read;
  char buffer[BUFFER_SIZE];

  //Clear the buffer.
  memset (buffer, 0, BUFFER_SIZE);

  //Formulate the queue name which is a GUID + the parent PID + the child PID.
  queue_name_size = 1 + snprintf(queue_name, 128, QUEUE_NAME, getppid(), getpid());

  DEBUG("QUEUE: %s\n", queue_name);
  DEBUG("Parent PID: %d\n", getppid());
  DEBUG("PID: %d\n", getpid());

  //Unlink the queue if it exists.
  mq_unlink(queue_name);

  //Open the message queue.
  queue_handle = mq_open(queue_name, O_RDWR | O_CREAT, S_IRWXU | S_IRWXG, NULL);
  if (-1 == queue_handle) {
    return ERROR;
  }

  bytes_read = mq_receive(queue_handle, )

  //Still have an issue if the signal is sent before we have the chance
  //to register our signal handler.

  DEBUG("AFTER SLEEP 1\n");

  sem_init(&sem, 0, 0);

  act.sa_sigaction = sighandler;
  act.sa_flags = SA_SIGINFO;
  sigemptyset(&act.sa_mask);

  for(i = 0; i < HANDLED_SIGNALS_SIZE; ++i) {
    sigaction(HANDLED_SIGNALS[i], &act, NULL);
  }

  DEBUG("BEFORE WAIT 1\n");

  while(((s = sem_wait(&sem)) != 0 || (s != -1 && errno != EINTR)) && !signal_received) {
    ;
  }

  sem_destroy(&sem);

  DEBUG("%s\n", "AFTER 1");

//  sigprocmask(SIG_SETMASK, &mask_old, NULL);
//
//  DEBUG("%s\n", "AFTER 2");
//
//  //Disable buffering on stdout.
//  //setvbuf(stdout, NULL, _IONBF, 0);
//
//  DEBUG("%s\n", "Executing process...");
//
//  char *temp[] = { NULL, "/tmp/blah", NULL };
//  temp[0] = "test.sh";
//
  chdir("/home/sysadmin/work/jcommon/process/src/main");

  DEBUG("%s\n", "AFTER 2");

  char *temp[] = { NULL, ".", NULL };
  temp[0] = "ls";
  ret = __execvpe("ls", temp, NULL);

//
//  //Replace the current process w/ the desired real work.
//  //ret = execve("test.sh", temp, NULL);
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