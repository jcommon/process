#include <stdio.h>

int main(int argc, const char *argv[]) {
  setvbuf(stdout, NULL, _IONBF, 0);
  fprintf(stdout, "%s\n", "Hello world");
}