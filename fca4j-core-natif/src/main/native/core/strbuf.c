#include "strbuf.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>

StrBuf sb_new(void) {
    StrBuf sb; sb.cap = 4096; sb.len = 0;
    sb.buf = (char*)malloc(sb.cap);
    sb.buf[0] = '\0';
    return sb;
}

static void sb_ensure(StrBuf *sb, int extra) {
    while (sb->len + extra + 1 >= sb->cap) {
        sb->cap *= 2;
        sb->buf = (char*)realloc(sb->buf, sb->cap);
    }
}

void sb_append(StrBuf *sb, const char *s) {
    int slen = (int)strlen(s);
    sb_ensure(sb, slen);
    memcpy(sb->buf + sb->len, s, slen);
    sb->len += slen;
    sb->buf[sb->len] = '\0';
}

void sb_printf(StrBuf *sb, const char *fmt, ...) {
    char tmp[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(tmp, sizeof(tmp), fmt, args);
    va_end(args);
    sb_append(sb, tmp);
}

void sb_append_json_str(StrBuf *sb, const char *s) {
    sb_append(sb, "\"");
    for (; *s; s++) {
        if      (*s == '"')  sb_append(sb, "\\\"");
        else if (*s == '\\') sb_append(sb, "\\\\");
        else if (*s == '\n') sb_append(sb, "\\n");
        else { sb_ensure(sb, 1); sb->buf[sb->len++] = *s; sb->buf[sb->len] = '\0'; }
    }
    sb_append(sb, "\"");
}
