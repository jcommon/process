#include "common.h"

int main(int argc, const char *argv[]) {
  //Echoes everything written via stdin to stdout.
  //A blank line ends exits the program.

  for(;;) {
    char* line;
    bool done;

    line =  read_single_line();
    done = (strncmp(line, "\n") == 0);

    if (!done)
      printf("%s", line);

    free(line);
    if (done)
      return 0;
  }
  return 0;
}
