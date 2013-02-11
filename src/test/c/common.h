#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <limits.h>
#include <errno.h>

unsigned int read_unsigned_int(const char* str, bool* success);
char* read_single_line(void);

unsigned int read_unsigned_int(const char* str, bool* success) {
  char *endptr;
  unsigned int val;

  val = strtoul(str, &endptr, 10);

  if ((errno == ERANGE && (val == LONG_MAX || val == LONG_MIN)) || (errno != 0 && val == 0)) {
    fprintf(stderr, "Invalid value: %s\n", str);
    *success = false;
    return 0;
  }

  if (endptr == str) {
    fprintf(stderr, "Invalid value: %s\n", str);
    *success = false;
    return 0;
  }

  *success = true;
  return val;
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
