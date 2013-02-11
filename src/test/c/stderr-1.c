#include "common.h"

int main(int argc, const char *argv[]) {
  //Prints all arguments provided in argv on separate lines to stderr.

  int i, j;
  bool success;
  unsigned int repeat;

  //Ensure stderr is unbuffered.
  setvbuf(stderr, NULL, _IONBF, 0);

  if (argc < 3) {
    fprintf(stderr, "Usage: %s repeat value [value]*\n", argv[0]);
    return 1;
  }

  repeat = read_unsigned_int(argv[1], &success);
  if (!success) {
    return 1;
  }

  for (i = 0; i < repeat; ++i) {
    for (j = 2; j < argc; ++j) {
      fprintf(stderr, "%s\n", argv[j]);
    }
  }

  return 0;
}
