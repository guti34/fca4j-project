/*
 * strbuf.h — Buffer de chaîne dynamique pour la sérialisation JSON
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_STRBUF_H
#define FCA4J_STRBUF_H

typedef struct { char *buf; int len, cap; } StrBuf;

StrBuf sb_new(void);
void   sb_append(StrBuf *sb, const char *s);
void   sb_printf(StrBuf *sb, const char *fmt, ...);
void   sb_append_json_str(StrBuf *sb, const char *s);

#endif /* FCA4J_STRBUF_H */
