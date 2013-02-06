#include <stdlib.h>
#include <stdio.h>
#include <stdbool.h>

char* read_single_line(void);

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

char* read_single_line(void) {
  char * line = malloc(100), * linep = line;
  size_t lenmax = 100, len = lenmax;
  int c;

  if(line == NULL)
    return NULL;

  for(;;) {
    c = fgetc(stdin);
    if(c == EOF)
      break;

    if(--len == 0) {
      len = lenmax;
      char * linen = realloc(linep, lenmax *= 2);

      if(linen == NULL) {
        free(linep);
        return NULL;
      }
      line = linen + (line - linep);
      linep = linen;
    }

    if((*line++ = c) == '\n')
      break;
  }
  *line = '\0';
  return linep;
}