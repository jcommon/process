#include <stdlib.h>
#include <limits.h>
#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <semaphore.h>
#include <stdbool.h>
#include <stdint.h>

#define execvpe __execvpe

#define READY_VALUE 0x0

#define MIN(a,b) ((a) < (b) ? a : b)

#define STDIN  fileno(stdin)
#define STDOUT fileno(stdout)
#define STDERR fileno(stderr)

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

static void usage() {
  INFO("Usage: ");
  INFO("  spawn <stdin pipe fd> <stdout pipe fd> <stderr pipe fd>");
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

static bool read_byte(const int fd, char *value, int *bytes_read) {
  *bytes_read = read(fd, value, 1);
  if (*bytes_read != 1) {
    return false;
  }

  return true;
}

static bool read_int(const int fd, int32_t *value, int *bytes_read) {
  *bytes_read = read(fd, value, 4);
  if (*bytes_read != 4 || *value < 0) {
    *value = 0;
    return false;
  }

  return true;
}

static bool read_line(const int fd, char **line, size_t *line_len, int32_t *line_size) {
  int32_t msg_size;
  size_t msg_len = 0;
  int32_t bytes_read;
  int32_t total_read_so_far = 0;
  char* buffer;

  //Read a message providing the size of the message (line).
  if (!read_int(fd, &msg_size, &bytes_read)) {
    ERROR("Error reading line.");
    *line = NULL;
    *line_len = 0;
    *line_size =0;
    return false;
  }

  if (msg_size == 0) {
    //INFO("Empty line.");
    *line = NULL;
    *line_len = 0;
    *line_size = 0;
    return true;
  }

  //INFO("SIZE IS: %d (%d bytes)", msg_size, bytes_read);

  buffer = (char*)malloc(sizeof(char) * msg_size);

  if (buffer == NULL) {
    *line = NULL;
    *line_len = 0;
    *line_size = 0;
    return false;
  }

  //Continue reading in bytes until we've read in everything we need to.
  while((bytes_read = read(fd, buffer + total_read_so_far, MIN(msg_size, 1024))) > 0 && (total_read_so_far += bytes_read) < msg_size)
    ;

  //Get the length of the string.
  msg_len = strnlen(buffer, msg_size);
  //msg_len = msg_size + 1;

  //Provide this info. to the caller.
  *line = buffer;
  *line_len = msg_len;
  *line_size = msg_size;
  return true;
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

  int stdin_pipe_fd, stdout_pipe_fd, stderr_pipe_fd;

  char *working_directory;
  size_t line_len;
  int32_t line_size, bytes_read;

  char** child_argv;
  int32_t child_argc;
  char* arg;

  char ready;

  int i, ret;

  //setvbuf(stdout, NULL, _IONBF, 0);

  if (argc != 4) {
    ERROR("Invalid arguments.");
    usage();
    return EXIT_ERROR;
  }

  if (!parse_to_int(argv[1], &stdin_pipe_fd)) {
    ERROR("Invalid <stdin pipe fd>.");
    usage();
    return EXIT_ERROR;
  }

  if (!parse_to_int(argv[2], &stdout_pipe_fd)) {
    ERROR("Invalid <stdout pipe fd>.");
    usage();
    return EXIT_ERROR;
  }

  if (!parse_to_int(argv[3], &stderr_pipe_fd)) {
    ERROR("Invalid <stderr pipe fd>.");
    usage();
    return EXIT_ERROR;
  }

  dup2(stdin_pipe_fd, STDIN);
  dup2(stdout_pipe_fd, STDOUT);
  dup2(stderr_pipe_fd, STDERR);

  close(stdin_pipe_fd);
  close(stdout_pipe_fd);
  close(stderr_pipe_fd);

  if (!read_line(STDIN, &working_directory, &line_len, &line_size)) {
    ERROR("Unable to read the working directory.");
    goto ERROR;
  }

  //If we don't have an empty string, then try and change the directory.
  if (line_len > 0) {
    if (chdir(working_directory) != 0) {
      //There was an error changing the directory.
      //Ignore for now? If we do that, then the CWD will be the parent process' CWD.
      //ERROR("Unable to update the current working directory.");
    }
  }

  if (working_directory != NULL) {
    free(working_directory);
  }

  //Time to stream the arguments.

  //Start by reading in the number of expected arguments (argc, effectively).
  if (!read_int(STDIN, &child_argc, &bytes_read)) {
    ERROR("Unable to determine the number of arguments.");
    goto ERROR;
  }

  if (child_argc <= 0) {
    ERROR("Expected at least one child argument.");
    goto ERROR;
  }

  //INFO("Expecting %d arguments.", child_argc);

  child_argv = (char**)malloc(sizeof(char*) * child_argc);

  if (child_argv == NULL) {
    ERROR("Unable to allocate memory for the child process arguments.");
    goto ERROR;
  }

  for(i = 0; i < child_argc; i++) {
    //INFO("Reading argument %d", i);
    if (!read_line(STDIN, &arg, &line_len, &line_size)) {
      int so_far = i;

      ERROR("Unable to read an argument.");

      //Free everything up until this point.
      for(i = 0; i < so_far; ++i) {
        free(child_argv[i]);
      }

      //Free the array itself as well.
      free(child_argv);

      goto ERROR;
    }
    child_argv[i] = arg;
  }

  //Send message to parent process letting them know we're almost
  //ready to begin execution.

  //Wait for parent to send one last message saying it's now done
  //processing and that we can begin execution.

  //INFO("Waiting for parent process to signal it's ready.");

  if (!read_byte(STDIN, &ready, &bytes_read) || ready != READY_VALUE) {
    ERROR("Failed to receive notification from parent process to begin execution.");
    goto ERROR;
  }

  //This should only return if there was a problem.
  ret = execvpe(child_argv[0], child_argv, NULL);

  //Free everything up until this point.
  for(i = 0; i < child_argc; ++i) {
    free(child_argv[i]);
  }

  //Free the array itself as well.
  free(child_argv);

  return ret;

ERROR:
  if (working_directory != NULL) {
    free(working_directory);
  }
  return EXIT_ERROR;
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