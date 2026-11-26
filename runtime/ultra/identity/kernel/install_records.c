// SPDX-License-Identifier: GPL-3.0-only
/* Work-only, MapleSU-owned extraction of protected Android PM disk records.
 * No I/O, cache, user path, grant, hook, logging, or success fallback. */
#include "install_records.h"
#include "manager_apk_identity.h"
#ifdef __KERNEL__
#include <linux/string.h>
#else
#include <string.h>
#endif
#define MAX_DEPTH 64U
#define MAX_ATTRS 64U
struct span { const msu_rec_u8 *p; size_t n; };
struct value { struct span s; msu_rec_u32 number; unsigned int kind; };
struct attr { struct span name; struct value value; };
struct event {
    struct span name;
    struct attr attrs[MAX_ATTRS];
    unsigned int count;
    int type, self;
};
struct document_scratch { struct event event; struct span stack[MAX_DEPTH]; };
_Static_assert(sizeof(struct document_scratch) <= sizeof(((struct msu_record_workspace *)0)->parser_scratch), "parser scratch");
struct parser {
    struct msu_record_bytes src;
    size_t pos;
    unsigned int refs;
    int abx, started, ended;
    struct msu_record_workspace *ws;
};
static int same(struct span a, struct span b)
{ return a.n == b.n && !memcmp(a.p, b.p, a.n); }
static int is(struct span a, const char *b)
{ size_t n = strlen(b); return a.n == n && !memcmp(a.p, b, n); }
static int space(msu_rec_u8 c)
{ return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
static int white(struct span s)
{ size_t i; for (i = 0; i < s.n; ++i) if (!space(s.p[i])) return 0; return 1; }
static int take(struct parser *p, size_t n, struct span *s)
{
    if (n > p->src.length - p->pos) return -1;
    if (s) { s->p = p->src.data + p->pos; s->n = n; }
    p->pos += n; return 0;
}
static int byte(struct parser *p)
{ return p->pos < p->src.length ? p->src.data[p->pos++] : -1; }
static int read_be16(struct parser *p)
{ int a = byte(p), b = byte(p); return a < 0 || b < 0 ? -1 : (a << 8) | b; }
static int utf(struct parser *p, struct span *s)
{ int n = read_be16(p); return n < 0 ? -1 : take(p, (size_t)n, s); }
static int intern(struct parser *p, struct span *s)
{
    int ref = read_be16(p);
    if (ref < 0) return -1;
    if (ref == 65535) {
        if (utf(p, s)) return -1;
        if (p->refs < MSU_RECORD_REFS) {
            p->ws->refs[p->refs].offset = (msu_rec_u32)(s->p - p->src.data);
            p->ws->refs[p->refs++].length = (msu_rec_u16)s->n;
        }
    } else {
        if ((unsigned int)ref >= p->refs) return -1;
        s->p = p->src.data + p->ws->refs[ref].offset;
        s->n = p->ws->refs[ref].length;
    }
    return 0;
}
static int add_attr(struct event *e, struct attr a)
{
    unsigned int i;
    if (e->count == MAX_ATTRS) return -1;
    for (i = 0; i < e->count; ++i) if (same(e->attrs[i].name, a.name)) return -1;
    e->attrs[e->count++] = a; return 0;
}
static int abx_value(struct parser *p, int type, struct value *v)
{
    struct span s = {0};
    size_t i;
    memset(v, 0, sizeof(*v));
    v->kind = (unsigned int)type;
    switch (type) {
    case 0x10: case 0xc0: case 0xd0: return 0;
    case 0x20: return utf(p, &v->s);
    case 0x30: return intern(p, &v->s);
    case 0x40: case 0x50: return utf(p, &v->s);
    case 0x60: case 0x70:
        if (take(p, 4, &s)) return -1;
        for (i = 0; i < s.n; ++i) v->number = (v->number << 8) | s.p[i];
        return 0;
    case 0x80: case 0x90:
        if (take(p,8,&s)) return -1;
        v->s = s;
        return 0;
    case 0xb0: return take(p,8,NULL);
    case 0xa0: return take(p, 4, NULL);
    default: return -1;
    }
}
static int next_abx(struct parser *p, struct event *e)
{
    int t = byte(p), k;
    struct attr a;
    struct value v;
    if (t < 0 || p->ended) return -1;
    switch (t & 15) {
    case 0:
        if (p->started || t != 0x10 || p->pos != 5) return -1;
        p->started = 1; e->type = 4; return 0;
    case 1:
        if (t != 0x11 || p->pos != p->src.length) return -1;
        p->ended = 1; e->type = 0; return 0;
    case 2:
        if (t != 0x32 || intern(p, &e->name)) return -1;
        e->type = 1;
        while (p->pos < p->src.length && (p->src.data[p->pos] & 15) == 15) {
            k = byte(p);
            if (intern(p, &a.name) || abx_value(p, k & 0xf0, &a.value) || add_attr(e, a)) return -1;
        }
        return 0;
    case 3:
        if (t != 0x33 || intern(p, &e->name)) return -1;
        e->type = 2; return 0;
    case 4: case 5: case 7:
        if (abx_value(p, t & 0xf0, &v) || (v.kind != 0x20 && v.kind != 0x30) || !white(v.s)) return -1;
        e->type = 4; return 0;
    case 8: case 9:
        if (abx_value(p, t & 0xf0, &v) || v.kind != 0x20) return -1;
        e->type = 4; return 0;
    default: return -1;
    }
}
static int namechar(msu_rec_u8 c, int first)
{
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':') return 1;
    return !first && ((c >= '0' && c <= '9') || c == '-' || c == '.');
}
static int name(struct parser *p, struct span *s)
{
    size_t start = p->pos;
    if (p->pos == p->src.length || !namechar(p->src.data[p->pos++], 1)) return -1;
    while (p->pos < p->src.length && namechar(p->src.data[p->pos], 0)) ++p->pos;
    s->p = p->src.data + start; s->n = p->pos - start;
    return s->n > 255 ? -1 : 0;
}
static void whitespace(struct parser *p)
{ while (p->pos < p->src.length && space(p->src.data[p->pos])) ++p->pos; }
static int literal(struct parser *p, const char *s)
{
    size_t n = strlen(s);
    if (n > p->src.length - p->pos || memcmp(p->src.data + p->pos, s, n)) return 0;
    p->pos += n; return 1;
}
static int through(struct parser *p, const char *end)
{
    size_t n = strlen(end);
    while (n <= p->src.length - p->pos) {
        if (literal(p, end)) return 0;
        ++p->pos;
    }
    return -1;
}
static int next_text(struct parser *p, struct event *e)
{
    struct attr a;
    size_t start;
    int quote, closing;
    whitespace(p);
    if (p->pos == p->src.length) { e->type = 0; return 0; }
    if (!literal(p, "<")) return -1;
    if (literal(p, "!--")) { e->type = 4; return through(p, "-->"); }
    if (literal(p, "?")) { e->type = 4; return through(p, "?>"); }
    closing = literal(p, "/");
    if (name(p, &e->name)) return -1;
    if (closing) {
        whitespace(p);
        if (!literal(p, ">")) return -1;
        e->type = 2; return 0;
    }
    e->type = 1;
    for (;;) {
        start = p->pos;
        whitespace(p);
        if (literal(p, "/>")) { e->self = 1; return 0; }
        if (literal(p, ">")) return 0;
        if (p->pos == start || name(p, &a.name)) return -1;
        whitespace(p);
        if (!literal(p, "=")) return -1;
        whitespace(p);
        quote = byte(p);
        if (quote != '\'' && quote != '"') return -1;
        start = p->pos;
        while (p->pos < p->src.length && p->src.data[p->pos] != quote) {
            if (p->src.data[p->pos] == '<' || !p->src.data[p->pos]) return -1;
            ++p->pos;
        }
        if (p->pos == p->src.length) return -1;
        a.value.s.p = p->src.data + start;
        a.value.s.n = p->pos++ - start;
        a.value.kind = 1; a.value.number = 0;
        if (add_attr(e, a)) return -1;
    }
}
static int ascii(struct value v, char *out, size_t cap)
{
    size_t i = 0, n = 0, start;
    unsigned int ch, digit, radix;
    if (v.kind != 1 && v.kind != 0x20 && v.kind != 0x30) return -1;
    while (i < v.s.n) {
        ch = v.s.p[i++];
        if (v.kind == 1 && ch == '&') {
            start = i;
            while (i < v.s.n && v.s.p[i] != ';' && i - start < 12) ++i;
            if (i == v.s.n || v.s.p[i] != ';') return -1;
            if (i - start == 3 && !memcmp(v.s.p + start, "amp", 3)) ch = '&';
            else if (i - start == 2 && !memcmp(v.s.p + start, "lt", 2)) ch = '<';
            else if (i - start == 2 && !memcmp(v.s.p + start, "gt", 2)) ch = '>';
            else if (i - start == 4 && !memcmp(v.s.p + start, "quot", 4)) ch = '"';
            else if (i - start == 4 && !memcmp(v.s.p + start, "apos", 4)) ch = '\'';
            else if (v.s.p[start] == '#') {
                size_t j = start + 1;
                ch = 0; radix = 10;
                if (j < i && v.s.p[j] == 'x') { radix = 16; ++j; }
                if (j == i) return -1;
                for (; j < i; ++j) {
                    msu_rec_u8 c = v.s.p[j];
                    if (c >= '0' && c <= '9') digit = c - '0';
                    else if (c >= 'a' && c <= 'f') digit = c - 'a' + 10;
                    else if (c >= 'A' && c <= 'F') digit = c - 'A' + 10;
                    else return -1;
                    if (digit >= radix || ch > (127U - digit) / radix) return -1;
                    ch = ch * radix + digit;
                }
            } else return -1;
            ++i;
        }
        if (!ch || ch > 127 || ch < 32 || n + 1 >= cap) return -1;
        out[n++] = (char)ch;
    }
    out[n] = 0; return 0;
}
static const struct value *get(const struct event *e, const char *key)
{ unsigned int i; for (i = 0; i < e->count; ++i) if (is(e->attrs[i].name, key)) return &e->attrs[i].value; return NULL; }
static int decimal(const char *s, msu_rec_u32 *out)
{
    msu_rec_u32 n = 0, d;
    if (!*s) return -1;
    do { if (*s < '0' || *s > '9') return -1; d = (msu_rec_u32)(*s++ - '0'); if (n > (0xffffffffU - d) / 10U) return -1; n = n * 10U + d; } while (*s);
    *out = n; return 0;
}
static int number(const struct value *v, msu_rec_u32 *out)
{
    char buf[16];
    if (!v) return -1;
    if (v->kind == 0x60 || v->kind == 0x70) { *out = v->number; return 0; }
    if (ascii(*v, buf, sizeof(buf))) return -1;
    return decimal(buf, out);
}
static int installed(const struct value *v)
{
    char buf[8];
    if (!v || v->kind == 0xc0) return 1;
    if (v->kind == 0xd0) return 0;
    if (ascii(*v, buf, sizeof(buf))) return -1;
    if (!strcmp(buf, "true") || !strcmp(buf, "1")) return 1;
    if (!strcmp(buf, "false") || !strcmp(buf, "0")) return 0;
    return -1;
}
static int valid_path(char *path)
{
    size_t i = 1, start = 1, n = strlen(path);
    if (n < 2 || path[0] != '/' || path[n - 1] == '/') return -1;
    for (; i <= n; ++i) {
        if (i < n && (path[i] == '\\' || (unsigned char)path[i] <= 32)) return -1;
        if (path[i] == '/' || !path[i]) {
            size_t part = i - start;
            if (!part || (part == 1 && path[start] == '.') ||
                (part == 2 && path[start] == '.' && path[start + 1] == '.')) return -1;
            start = i + 1;
        }
    }
    if (n >= 4 && !strcmp(path + n - 4, ".apk")) return 0;
    if (n + 10 > MSU_RECORD_PATH) return -1;
    memcpy(path + n, "/base.apk", 10); return 0;
}
static int package_list(struct msu_record_bytes b, msu_rec_u32 appid, const char *pkg, int shared_allowed)
{
    size_t p = 0, begin, lineend, uidbegin, uidend;
    unsigned int matches = 0;
    msu_rec_u32 uid;
    char num[16];
    struct span entry;
    if (!b.data || !b.length || b.length > MSU_RECORD_LIMIT) return -1;
    while (p < b.length) {
        begin = p;
        while (p < b.length && b.data[p] != '\n') { if (!b.data[p] || b.data[p] == '\r') return -1; ++p; }
        lineend = p;
        if (p < b.length) ++p;
        if (lineend == begin) return -1;
        uidbegin = begin;
        while (uidbegin < lineend && b.data[uidbegin] != ' ') ++uidbegin;
        entry.p = b.data + begin; entry.n = uidbegin - begin;
        if (!entry.n || uidbegin == lineend) return -1;
        ++uidbegin; uidend = uidbegin;
        while (uidend < lineend && b.data[uidend] != ' ') ++uidend;
        if (uidend == lineend || uidend - uidbegin >= sizeof(num)) return -1;
        memcpy(num, b.data + uidbegin, uidend - uidbegin); num[uidend - uidbegin] = 0;
        if (decimal(num, &uid)) return -1;
        if (is(entry, pkg)) { if (uid != appid || ++matches > 1) return -2; }
        else if (uid == appid && !shared_allowed) return -2;
    }
    return matches == 1 ? 0 : -2;
}
static int document(struct msu_record_bytes b, int user, msu_rec_u32 appid,
    const char *pkg, struct msu_record_workspace *ws, char *apk, int shared_allowed)
{
    struct parser p = {0};
    struct document_scratch *scratch = (void *)ws->parser_scratch;
    struct event *ep = &scratch->event;
    struct span *stack = scratch->stack;
    unsigned int depth = 0, roots = 0, matches = 0;
    const struct value *v, *uid, *shared;
    msu_rec_u32 id;
    char namebuf[256];
    int rc, target;
    if (!b.data || !b.length || b.length > MSU_RECORD_LIMIT) return -1;
    p.src = b; p.ws = ws;
    p.abx = b.length >= 4 && !memcmp(b.data, "ABX\0", 4);
    if (p.abx) p.pos = 4;
    else if (b.length >= 3 && !memcmp(b.data, "\xef\xbb\xbf", 3)) p.pos = 3;
    for (;;) {
        memset(ep, 0, sizeof(*ep));
        rc = p.abx ? next_abx(&p, ep) : next_text(&p, ep);
        if (rc) return -1;
        if (!ep->type) break;
        if (ep->type == 4) continue;
        if (ep->type == 2) {
            if (!depth || !same(stack[depth - 1], ep->name)) return -1;
            --depth; continue;
        }
        if (depth == MAX_DEPTH) return -1;
        if (!depth) {
            if (++roots != 1 || !is(ep->name, user ? "package-restrictions" : "packages")) return -1;
        }
        stack[depth++] = ep->name;
        if (depth == 2 && is(ep->name, user ? "pkg" : "package")) {
            v = get(ep, "name");
            if (!v || ascii(*v, namebuf, sizeof(namebuf))) return -1;
            target = !strcmp(namebuf, pkg);
            if (target && ++matches > 1) return -2;
            if (user) {
                if (target && installed(get(ep, "inst")) != 1) return shared_allowed ? -3 : -2;
            } else {
                uid = get(ep, "userId"); shared = get(ep, "sharedUserId");
                if (uid && number(uid, &id)) return -1;
                if (uid && id == appid && !target && !shared_allowed) return -2;
                if (shared && number(shared, &id)) return -1;
                if (shared && (target || id == appid) && !shared_allowed) return -2;
                if (target) {
                    if ((!uid && !(shared_allowed && shared)) ||
                        number(uid ? uid : shared, &id) || id != appid) return -2;
                    v = get(ep, "codePath");
                    if (!v || ascii(*v, apk, MSU_RECORD_PATH) || valid_path(apk)) return -1;
                }
            }
        }
        if (ep->self) --depth;
    }
    if (depth || roots != 1 || (!user && matches != 1)) return -2;
    return 0;
}
static int read_install_records(struct msu_record_bytes list,
    struct msu_record_bytes global, struct msu_record_bytes per_user,
    msu_rec_u32 uid, const char *pkg, struct msu_record_workspace *ws,
    struct msu_install_record *out, int shared_allowed)
{
    msu_rec_u32 appid = uid % 100000U;
    struct msu_install_record result = {0};
    int rc;
    if (out) memset(out, 0, sizeof(*out));
    if (!out || !ws || !pkg || !*pkg || uid > 2147483647U ||
        (!shared_allowed && (appid < 10000U || appid > 19999U)) ||
        (shared_allowed && uid != 1000U && uid < 2000U)) return -2;
    rc = package_list(list, appid, pkg, shared_allowed);
    if (rc) return rc;
    rc = document(global, 0, appid, pkg, ws, result.apk_path, shared_allowed);
    if (rc) return rc;
    if (per_user.length) {
        rc = document(per_user, 1, appid, pkg, ws, result.apk_path, shared_allowed);
        if (rc) return rc;
    } else if (per_user.data) return -1;
    result.full_uid = uid; result.user_id = uid / 100000U; result.app_id = appid;
    *out = result; return 0;
}

int msu_read_install_records(struct msu_record_bytes list,
    struct msu_record_bytes global, struct msu_record_bytes per_user,
    msu_rec_u32 uid, const char *pkg, struct msu_record_workspace *ws,
    struct msu_install_record *out)
{
    return read_install_records(list, global, per_user, uid, pkg, ws, out, 0);
}
/* App SU shares one Linux credential authority across all packages in an appId.
 * The Manager entry above deliberately continues to reject shared identities. */
int msu_read_observed_install_records(struct msu_record_bytes list,
    struct msu_record_bytes global, struct msu_record_bytes per_user,
    msu_rec_u32 uid, const char *pkg, struct msu_record_workspace *ws,
    struct msu_install_record *out)
{
    return read_install_records(list, global, per_user, uid, pkg, ws, out, 1);
}

static int observed_hex_digit(msu_rec_u8 value)
{
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'f') return value - 'a' + 10;
    if (value >= 'A' && value <= 'F') return value - 'A' + 10;
    return -1;
}
static int observed_time(const struct value *value,msu_rec_u64 *result)
{
    size_t i;
    msu_rec_u64 number = 0;
    if (!value) return 1;
    if (value->kind == 0x80 || value->kind == 0x90) {
        if (value->s.n != 8) return -1;
        for (i = 0; i < 8; ++i) number = (number << 8) | value->s.p[i];
    } else {
        if (!value->s.n || value->s.n > 16) return -1;
        for (i = 0; i < value->s.n; ++i) {
            int digit = observed_hex_digit(value->s.p[i]);
            if (digit < 0 || number > (~(msu_rec_u64)0 - digit) / 16) return -1;
            number = number * 16 + digit;
        }
    }
    if (!number || number > 0x7fffffffffffffffULL) return -1;
    *result = number;
    return 0;
}
static int observed_certificate(struct parser *parser,const struct value *key,
    msu_rec_u32 index,struct msu_observed_identity_workspace *workspace,
    int (*hash)(const void *,size_t,msu_rec_u8[32]),
    struct msu_observed_record_identity *identity,int signer)
{
    struct msu_record_ref *reference;
    struct span encoded;
    msu_rec_u8 kind,digest[32];
    size_t count,i;
    if (index >= MSU_RECORD_REFS) return -1;
    reference = &workspace->certificates[index];
    if (key) {
        if (!key->s.n || key->s.n > 16384 || key->s.p < parser->src.data ||
            (size_t)(key->s.p - parser->src.data) > parser->src.length - key->s.n) return -1;
        kind = key->kind == 0x40 || key->kind == 0x50 ? 1 : 2;
        if (reference->length && (reference->length != key->s.n ||
            workspace->certificate_kinds[index] != kind ||
            memcmp(parser->src.data + reference->offset,key->s.p,key->s.n))) return -1;
        reference->offset = key->s.p - parser->src.data;
        reference->length = key->s.n;
        workspace->certificate_kinds[index] = kind;
    }
    if (!signer) return 0;
    if (!reference->length) return -1;
    encoded.p = parser->src.data + reference->offset; encoded.n = reference->length;
    if (workspace->certificate_kinds[index] == 1) {
        if (encoded.n > sizeof(workspace->certificate)) return -1;
        memcpy(workspace->certificate,encoded.p,encoded.n); count = encoded.n;
    } else {
        if (encoded.n & 1 || encoded.n / 2 > sizeof(workspace->certificate)) return -1;
        count = encoded.n / 2;
        for (i = 0; i < count; ++i) {
            int high = observed_hex_digit(encoded.p[2*i]);
            int low = observed_hex_digit(encoded.p[2*i+1]);
            if (high < 0 || low < 0) return -1;
            workspace->certificate[i] = (high << 4) | low;
        }
    }
    if (hash(workspace->certificate,count,digest)) return -1;
    for (i = 0; i < identity->signer_count; ++i)
        if (!memcmp(identity->signers[i],digest,32)) return 0;
    if (identity->signer_count == MSU_OBSERVED_SIGNERS) return -1;
    memcpy(identity->signers[identity->signer_count++],digest,32);
    return 0;
}
static int observed_identity_document(struct msu_record_bytes bytes,int user,
    msu_rec_u32 appid,const char *package,struct msu_observed_identity_workspace *workspace,
    int (*hash)(const void *,size_t,msu_rec_u8[32]),struct msu_observed_record_identity *identity)
{
    struct parser parser = {0};
    struct document_scratch *scratch = (void *)workspace->parser.parser_scratch;
    struct event *event = &scratch->event;
    struct span *stack = scratch->stack;
    unsigned int depth = 0,roots = 0,packages = 0,sigs_expected = 0,sigs_seen = 0;
    int target = 0,signature = 0,result,package_target = 0,package_signers = 0;
    char namebuf[256];
    if (!bytes.data || !bytes.length || bytes.length > MSU_RECORD_LIMIT) return -1;
    memset(workspace->certificates,0,sizeof(workspace->certificates));
    memset(workspace->certificate_kinds,0,sizeof(workspace->certificate_kinds));
    parser.src = bytes; parser.ws = &workspace->parser;
    parser.abx = bytes.length >= 4 && !memcmp(bytes.data,"ABX\0",4);
    if (parser.abx) parser.pos = 4;
    else if (bytes.length >= 3 && !memcmp(bytes.data,"\xef\xbb\xbf",3)) parser.pos = 3;
    for (;;) {
        const struct value *value;
        msu_rec_u32 id,index;
        memset(event,0,sizeof(*event));
        result = parser.abx ? next_abx(&parser,event) : next_text(&parser,event);
        if (result) return -1;
        if (!event->type) break;
        if (event->type == 4) continue;
        if (event->type == 2) {
            if (!depth || !same(stack[depth-1],event->name)) return -1;
            if (depth == 3 && signature) {
                if (sigs_seen != sigs_expected) return -1;
                signature = 0;
            }
            if (depth == 2) target = 0;
            --depth; continue;
        }
        if (depth == MAX_DEPTH) return -1;
        if (!depth && (++roots != 1 || !is(event->name,user ? "package-restrictions" : "packages"))) return -1;
        stack[depth++] = event->name;
        if (depth == 2 && is(event->name,user ? "pkg" : "package")) {
            value = get(event,"name");
            if (!value || ascii(*value,namebuf,sizeof(namebuf))) return -1;
            target = !strcmp(namebuf,package); package_target = target;
            if (target && ++packages != 1) return -1;
            if (target) {
                if (user && installed(get(event,"inst")) != 1) return -1;
                value = get(event,user ? "first-install-time" : "it");
                result = observed_time(value,&identity->first_install_ms);
                if (result < 0) return -1;
            }
        } else if (!user && depth == 2 && is(event->name,"shared-user")) {
            value = get(event,"userId");
            if (!value || number(value,&id)) return -1;
            target = id == appid && !package_signers; package_target = 0;
        }
        if (!user && depth == 3 && target && is(event->name,"sigs")) {
            value = get(event,"count");
            if (!value || number(value,&id) || !id || id > MSU_OBSERVED_SIGNERS) return -1;
            if (package_target && !package_signers) {
                memset(identity->signers,0,sizeof(identity->signers));
                identity->signer_count = 0; package_signers = 1;
            }
            signature = 1; sigs_expected = id; sigs_seen = 0;
        }
        if (!user && is(event->name,"cert")) {
            value = get(event,"index");
            if (!value || number(value,&index)) return -1;
            result = observed_certificate(&parser,get(event,"key"),index,workspace,hash,identity,
                signature && depth == 4 && target);
            if (result) return result;
            if (signature && depth == 4 && target) ++sigs_seen;
        }
        if (event->self) {
            if (depth == 3 && signature && is(event->name,"sigs")) return -1;
            if (depth == 2) target = 0;
            --depth;
        }
    }
    return depth || roots != 1 || (!user && packages != 1) ? -1 : 0;
}
int msu_read_observed_record_identity(struct msu_record_bytes global,
    struct msu_record_bytes per_user,msu_rec_u32 appid,const char *package,
    struct msu_observed_identity_workspace *workspace,
    int (*hash)(const void *,size_t,msu_rec_u8[32]),struct msu_observed_record_identity *identity)
{
    int result;
    if (!workspace || !hash || !identity || !package) return -1;
    memset(identity,0,sizeof(*identity));
    result = observed_identity_document(global,0,appid,package,workspace,hash,identity);
    if (!result && per_user.length)
        result = observed_identity_document(per_user,1,appid,package,workspace,hash,identity);
    if (!result && (!identity->first_install_ms || !identity->signer_count)) result = -1;
    return result;
}

/* Signature index references are global to packages.xml, unlike ABX's string
 * references. Bound just the selected Manager's chain; resolve its definitions
 * with a second streaming pass instead of allocating a global cert table. */
#define MSU_SIGNING_CERTS 32U
#define MSU_SIGNING_CERT_BYTES 1024U
struct signing_ref { msu_rec_u32 index, flags; size_t at; };
struct signing_record {
    struct signing_ref current_signer, past[MSU_SIGNING_CERTS];
    msu_rec_u32 past_count, scheme;
};
static void start_document(struct parser *p, struct msu_record_bytes b,
    struct msu_record_workspace *ws)
{
    memset(p, 0, sizeof(*p)); p->src = b; p->ws = ws;
    p->abx = b.length >= 4 && !memcmp(b.data, "ABX\0", 4);
    if (p->abx) p->pos = 4;
    else if (b.length >= 3 && !memcmp(b.data, "\xef\xbb\xbf", 3)) p->pos = 3;
}
static __attribute__((__noinline__)) int signing_record(struct msu_record_bytes b, const char *pkg,
    struct msu_record_workspace *ws, struct signing_record *out)
{
    struct parser p;
    struct document_scratch *scratch = (void *)ws->parser_scratch;
    struct event *e = &scratch->event;
    struct span *stack = scratch->stack;
    unsigned int depth = 0, roots = 0, matches = 0, target = 0;
    unsigned int sigs = 0, pasts = 0, current_signer = 0, past = 0;
    msu_rec_u32 expected_past = 0, count;
    char namebuf[256];
    if (!b.data || !b.length || b.length > MSU_RECORD_LIMIT) return -1;
    memset(out, 0, sizeof(*out)); start_document(&p, b, ws);
    for (;;) {
        memset(e, 0, sizeof(*e));
        if ((p.abx ? next_abx(&p, e) : next_text(&p, e))) return -1;
        if (!e->type) break;
        if (e->type == 4) continue;
        if (e->type == 2) {
            if (!depth || !same(stack[depth - 1], e->name)) return -1;
            if (depth == 2) target = 0;
            --depth; continue;
        }
        if (depth == MAX_DEPTH) return -1;
        if (!depth && (++roots != 1 || !is(e->name, "packages"))) return -1;
        stack[depth++] = e->name;
        if (depth == 2 && is(e->name, "package")) {
            const struct value *v = get(e, "name");
            if (!v || ascii(*v, namebuf, sizeof(namebuf))) return -1;
            target = !strcmp(namebuf, pkg);
            if (target && ++matches != 1) return -1;
        } else if (target && depth == 3 && is(e->name, "sigs")) {
            if (++sigs != 1 || number(get(e, "count"), &count) || count != 1 ||
                number(get(e, "schemeVersion"), &out->scheme) ||
                out->scheme < 1 || out->scheme > 4) return -1;
        } else if (target && depth == 4 && is(stack[2], "sigs")) {
            if (is(e->name, "cert")) {
                if (++current_signer != 1 || number(get(e, "index"), &out->current_signer.index)) return -1;
                out->current_signer.at = p.pos;
            } else if (is(e->name, "pastSigs")) {
                if (++pasts != 1 || out->scheme < 3 ||
                    number(get(e, "count"), &expected_past) ||
                    expected_past < 2 || expected_past > MSU_SIGNING_CERTS) return -1;
            } else return -1;
        } else if (target && depth == 5 && is(stack[2], "sigs") &&
                   is(stack[3], "pastSigs")) {
            if (!is(e->name, "cert") || past == MSU_SIGNING_CERTS ||
                number(get(e, "index"), &out->past[past].index) ||
                number(get(e, "flags"), &out->past[past].flags)) return -1;
            out->past[past++].at = p.pos;
        } else if (target && depth > 3 && is(stack[2], "sigs")) return -1;
        if (e->self) { if (depth == 2) target = 0; --depth; }
    }
    if (depth || roots != 1 || matches != 1 || sigs != 1 || current_signer != 1 ||
        past != expected_past || (past && out->past[past - 1].index != out->current_signer.index)) return -1;
    out->past_count = past; return 0;
}
static int hex_digit(msu_rec_u8 c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
static int signing_key(struct value key, msu_rec_u8 *cert, size_t *bytes)
{
    size_t i;
    /* TypedXml's BYTES_HEX is binary; text XML emits the same DER as hex. */
    if (key.kind == 0x50) {
        if (!key.s.n || key.s.n > MSU_SIGNING_CERT_BYTES) return -1;
        memcpy(cert, key.s.p, key.s.n); *bytes = key.s.n; return 0;
    }
    if (key.kind != 1 && key.kind != 0x20 && key.kind != 0x30) return -1;
    if (!key.s.n || key.s.n % 2 || key.s.n / 2 > MSU_SIGNING_CERT_BYTES) return -1;
    for (i = 0; i < key.s.n; i += 2) {
        int a = hex_digit(key.s.p[i]), b = hex_digit(key.s.p[i + 1]);
        if (a < 0 || b < 0) return -1;
        cert[i / 2] = (msu_rec_u8)((a << 4) | b);
    }
    *bytes = key.s.n / 2; return 0;
}
static __attribute__((__noinline__)) int signing_digests(struct msu_record_bytes b, struct msu_record_workspace *ws,
    const struct signing_record *record, void *context,
    int (*sha256)(void *, const void *, size_t, msu_rec_u8[32]),
    msu_rec_u8 digests[MSU_SIGNING_CERTS + 1][32], msu_rec_u32 *sizes)
{
    struct parser p;
    struct document_scratch *scratch = (void *)ws->parser_scratch;
    struct event *e = &scratch->event;
    unsigned char found[MSU_SIGNING_CERTS + 1] = {0};
    msu_rec_u8 certificate[MSU_SIGNING_CERT_BYTES], digest[32];
    unsigned int i;
    start_document(&p, b, ws);
    for (;;) {
        const struct value *key;
        msu_rec_u32 index;
        size_t bytes;
        int needed = 0;
        memset(e, 0, sizeof(*e));
        if ((p.abx ? next_abx(&p, e) : next_text(&p, e))) return -1;
        if (!e->type) break;
        if (e->type != 1 || !is(e->name, "cert") || !(key = get(e, "key"))) continue;
        if (number(get(e, "index"), &index)) return -1;
        for (i = 0; i <= record->past_count; ++i) {
            const struct signing_ref *ref = i ? &record->past[i - 1] : &record->current_signer;
            if (index == ref->index) {
                if (found[i] || p.pos > ref->at) return -1;
                needed = 1;
            }
        }
        if (!needed) continue;
        if (signing_key(*key, certificate, &bytes) || sha256(context, certificate, bytes, digest)) return -1;
        for (i = 0; i <= record->past_count; ++i) {
            const struct signing_ref *ref = i ? &record->past[i - 1] : &record->current_signer;
            if (index == ref->index) {
                found[i] = 1; sizes[i] = (msu_rec_u32)bytes;
                memcpy(digests[i], digest, 32);
            }
        }
    }
    for (i = 0; i <= record->past_count; ++i) if (!found[i]) return -1;
    return 0;
}
int msu_read_install_signing(struct msu_record_bytes global, const char *pkg,
    struct msu_record_workspace *ws, const struct msu_manager_apk_pin *pin,
    void *context, int (*sha256)(void *, const void *, size_t, msu_rec_u8[32]),
    struct msu_manager_apk_pin *verified_current)
{
    struct signing_record record;
    struct msu_manager_apk_pin result;
    msu_rec_u8 digests[MSU_SIGNING_CERTS + 1][32];
    msu_rec_u32 sizes[MSU_SIGNING_CERTS + 1];
    unsigned int i, j, pinned = 0;
    int exact;
    if (verified_current) memset(verified_current, 0, sizeof(*verified_current));
    if (!verified_current || !ws || !pin || !pkg || !*pkg || !sha256 ||
        !pin->certificate_size || pin->certificate_size > MSU_SIGNING_CERT_BYTES ||
        signing_record(global, pkg, ws, &record) ||
        signing_digests(global, ws, &record, context, sha256, digests, sizes)) return -1;
    exact = sizes[0] == pin->certificate_size && !memcmp(digests[0], pin->certificate_sha256, 32);
    if (!exact && record.past_count < 2) return -1;
    for (i = 1; i <= record.past_count; ++i) {
        for (j = 1; j < i; ++j) if (!memcmp(digests[j], digests[i], 32)) return -1;
        if (sizes[i] == pin->certificate_size && !memcmp(digests[i], pin->certificate_sha256, 32)) ++pinned;
    }
    if (record.past_count && memcmp(digests[record.past_count], digests[0], 32)) return -1;
    if (!exact && pinned != 1) return -1;
    result = *pin; result.certificate_size = sizes[0];
    memcpy(result.certificate_sha256, digests[0], 32);
    *verified_current = result; return 0;
}
