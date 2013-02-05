#include <stdlib.h>
#include <stdio.h>

int main(int argc, const char *argv[]) {
  //Prints all arguments provided in argv on separate lines to stderr.

  int i;
  for (i = 1; i < argc; ++i) {
    fprintf(stderr, "%s\n", argv[i]);
  }
  return 0;
}
