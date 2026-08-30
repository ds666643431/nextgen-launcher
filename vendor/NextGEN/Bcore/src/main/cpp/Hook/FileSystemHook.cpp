#include "FileSystemHook.h"
#include "Log.h"
#include "xdl.h"
#include "IO.h"
#include "Dobby/dobby.h"
#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <cstring>
#include <cstdlib>
#include <errno.h>
#include <dirent.h>
#include <unistd.h>
#include <limits.h>
#include <signal.h>
#include <sys/syscall.h>
#include <string>

#ifndef __NR_memfd_create
#define __NR_memfd_create 279   // arm64
#endif

// Inline (Dobby) hooks on the arm64 C library the guest actually runs under.
//
// This module replaces, on native-bridge emulators (MuMu/houdini: our arm64 lib on a
// x86_64 ART), the JNI-native hooks that SIGILL when they call their x86_64 originals.
// Everything here is arm64->arm64 (houdini-safe). It provides:
//   1. path redirection  (open/stat/... -> IO::redirectPath, mirrors UnixFileSystemHook)
//   2. uid virtualization (getuid/geteuid -> the guest's virtual uid; mirrors Onimod
//      setFakeUid / native uid hooks that our BinderHook did via JNI)
//   3. kill neutering    (a guest can't fatally signal itself into a restart loop)
//   4. /proc cloaking    (maps/smaps/mounts served filtered so apps can't see the
//      container's libraries/paths)

#define BLOCKED_PATH "/dev/null"

// ---------------------------------------------------------------- path redirect
static const char *fs_redirect(const char *path, char *buf, size_t bufsz) {
    if (path == nullptr) return path;
    const char *r = IO::redirectPath(path);
    if (r == path) return path;
    if (strcmp(r, BLOCKED_PATH) == 0) return BLOCKED_PATH;
    strncpy(buf, r, bufsz - 1);
    buf[bufsz - 1] = '\0';
    free((void *) r);   // replace() malloc'd it
    return buf;
}
#define RP(p) char _b[PATH_MAX]; const char *p##_r = fs_redirect(p, _b, sizeof(_b))

// ---------------------------------------------------------------- uid spoof state
static volatile int g_fake_uid = -1;   // <0 = disabled

// ---------------------------------------------------------------- /proc cloak
static int (*orig_openat)(int, const char *, int, ...) = nullptr;   // fwd (used by cloak)

static bool proc_is_cloakable(const char *p) {
    if (!p || !strstr(p, "/proc/")) return false;
    const char *base = strrchr(p, '/');
    if (!base) return false;
    return strcmp(base, "/maps") == 0 || strcmp(base, "/smaps") == 0 ||
           strcmp(base, "/mounts") == 0 || strcmp(base, "/mountinfo") == 0;
}
static bool line_hidden(const std::string &line) {
    // Conservatively hide only the container's injected native artifacts (the primary
    // "am I virtualized?" tell). We deliberately do NOT strip the guest's own redirected
    // data paths here, to avoid hiding mappings the guest legitimately needs.
    return line.find("libngcore") != std::string::npos ||
           line.find("libmodmenu")  != std::string::npos ||
           line.find("shadowhook")  != std::string::npos ||
           line.find("libdobby")    != std::string::npos;
}
// Returns a fresh readable fd with the container-hiding lines removed, or -1 to fall
// through to a normal open. Defensive: any failure -> -1.
static int serve_cloaked(const char *path) {
    int rfd = orig_openat ? orig_openat(AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0) : -1;
    if (rfd < 0) return -1;
    std::string all;
    char chunk[8192];
    ssize_t n;
    size_t cap = 0;
    while ((n = read(rfd, chunk, sizeof(chunk))) > 0) {
        all.append(chunk, (size_t) n);
        cap += (size_t) n;
        if (cap > (8u << 20)) break;   // 8MB safety cap
    }
    close(rfd);
    std::string out;
    out.reserve(all.size());
    size_t start = 0;
    while (start < all.size()) {
        size_t nl = all.find('\n', start);
        size_t end = (nl == std::string::npos) ? all.size() : nl + 1;
        std::string line = all.substr(start, end - start);
        if (!line_hidden(line)) out.append(line);
        start = end;
    }
    long mfd = syscall(__NR_memfd_create, "m", 0);
    if (mfd < 0) return -1;
    const char *d = out.data();
    size_t left = out.size();
    while (left > 0) {
        ssize_t w = write((int) mfd, d, left);
        if (w <= 0) { close((int) mfd); return -1; }
        d += w; left -= (size_t) w;
    }
    lseek((int) mfd, 0, SEEK_SET);
    return (int) mfd;
}

// ---------------------------------------------------------------- originals
static int (*orig_open)(const char *, int, ...) = nullptr;
static int (*orig_open64)(const char *, int, ...) = nullptr;
static int (*orig_openat64)(int, const char *, int, ...) = nullptr;
static int (*orig_stat)(const char *, void *) = nullptr;
static int (*orig_stat64)(const char *, void *) = nullptr;
static int (*orig_lstat)(const char *, void *) = nullptr;
static int (*orig_lstat64)(const char *, void *) = nullptr;
static int (*orig_fstatat)(int, const char *, void *, int) = nullptr;
static int (*orig_fstatat64)(int, const char *, void *, int) = nullptr;
static int (*orig_newfstatat)(int, const char *, void *, int) = nullptr;
static int (*orig_access)(const char *, int) = nullptr;
static int (*orig_faccessat)(int, const char *, int, int) = nullptr;
static ssize_t (*orig_readlink)(const char *, char *, size_t) = nullptr;
static ssize_t (*orig_readlinkat)(int, const char *, char *, size_t) = nullptr;
static int (*orig_mkdir)(const char *, mode_t) = nullptr;
static int (*orig_mkdirat)(int, const char *, mode_t) = nullptr;
static int (*orig_rmdir)(const char *) = nullptr;
static int (*orig_unlink)(const char *) = nullptr;
static int (*orig_unlinkat)(int, const char *, int) = nullptr;
static int (*orig_rename)(const char *, const char *) = nullptr;
static int (*orig_renameat)(int, const char *, int, const char *) = nullptr;
static int (*orig_chmod)(const char *, mode_t) = nullptr;
static int (*orig_fchmodat)(int, const char *, mode_t, int) = nullptr;
static int (*orig_chown)(const char *, uid_t, gid_t) = nullptr;
static int (*orig_lchown)(const char *, uid_t, gid_t) = nullptr;
static DIR *(*orig_opendir)(const char *) = nullptr;
static int (*orig_chdir)(const char *) = nullptr;
static int (*orig_truncate)(const char *, off_t) = nullptr;
static uid_t (*orig_getuid)(void) = nullptr;
static uid_t (*orig_geteuid)(void) = nullptr;
static int (*orig_kill)(pid_t, int) = nullptr;

// ---------------------------------------------------------------- replacements
static int new_open(const char *path, int flags, ...) {
    RP(path);
    if (proc_is_cloakable(path_r)) { int c = serve_cloaked(path_r); if (c >= 0) return c; }
    va_list ap; va_start(ap, flags); mode_t mode = va_arg(ap, mode_t); va_end(ap);
    return orig_open(path_r, flags, mode);
}
static int new_open64(const char *path, int flags, ...) {
    RP(path);
    if (proc_is_cloakable(path_r)) { int c = serve_cloaked(path_r); if (c >= 0) return c; }
    va_list ap; va_start(ap, flags); mode_t mode = va_arg(ap, mode_t); va_end(ap);
    return orig_open64(path_r, flags, mode);
}
static int new_openat(int fd, const char *path, int flags, ...) {
    RP(path);
    if (proc_is_cloakable(path_r)) { int c = serve_cloaked(path_r); if (c >= 0) return c; }
    va_list ap; va_start(ap, flags); mode_t mode = va_arg(ap, mode_t); va_end(ap);
    return orig_openat(fd, path_r, flags, mode);
}
static int new_openat64(int fd, const char *path, int flags, ...) {
    RP(path);
    if (proc_is_cloakable(path_r)) { int c = serve_cloaked(path_r); if (c >= 0) return c; }
    va_list ap; va_start(ap, flags); mode_t mode = va_arg(ap, mode_t); va_end(ap);
    return orig_openat64(fd, path_r, flags, mode);
}
static int new_stat(const char *path, void *buf)   { RP(path); return orig_stat(path_r, buf); }
static int new_stat64(const char *path, void *buf) { RP(path); return orig_stat64(path_r, buf); }
static int new_lstat(const char *path, void *buf)  { RP(path); return orig_lstat(path_r, buf); }
static int new_lstat64(const char *path, void *buf){ RP(path); return orig_lstat64(path_r, buf); }
static int new_fstatat(int fd, const char *path, void *buf, int flags)   { RP(path); return orig_fstatat(fd, path_r, buf, flags); }
static int new_fstatat64(int fd, const char *path, void *buf, int flags) { RP(path); return orig_fstatat64(fd, path_r, buf, flags); }
static int new_newfstatat(int fd, const char *path, void *buf, int flags){ RP(path); return orig_newfstatat(fd, path_r, buf, flags); }
static int new_access(const char *path, int mode)  { RP(path); return orig_access(path_r, mode); }
static int new_faccessat(int fd, const char *path, int mode, int flags) { RP(path); return orig_faccessat(fd, path_r, mode, flags); }
static ssize_t new_readlink(const char *path, char *buf, size_t sz) { RP(path); return orig_readlink(path_r, buf, sz); }
static ssize_t new_readlinkat(int fd, const char *path, char *buf, size_t sz) { RP(path); return orig_readlinkat(fd, path_r, buf, sz); }
static int new_mkdir(const char *path, mode_t mode){ RP(path); return orig_mkdir(path_r, mode); }
static int new_mkdirat(int fd, const char *path, mode_t mode) { RP(path); return orig_mkdirat(fd, path_r, mode); }
static int new_rmdir(const char *path)             { RP(path); return orig_rmdir(path_r); }
static int new_unlink(const char *path)            { RP(path); return orig_unlink(path_r); }
static int new_unlinkat(int fd, const char *path, int flags) { RP(path); return orig_unlinkat(fd, path_r, flags); }
static int new_rename(const char *o, const char *n) {
    char ob[PATH_MAX], nb[PATH_MAX];
    const char *o_r = fs_redirect(o, ob, sizeof(ob));
    const char *n_r = fs_redirect(n, nb, sizeof(nb));
    return orig_rename(o_r, n_r);
}
static int new_renameat(int ofd, const char *o, int nfd, const char *n) {
    char ob[PATH_MAX], nb[PATH_MAX];
    const char *o_r = fs_redirect(o, ob, sizeof(ob));
    const char *n_r = fs_redirect(n, nb, sizeof(nb));
    return orig_renameat(ofd, o_r, nfd, n_r);
}
static int new_chmod(const char *path, mode_t mode){ RP(path); return orig_chmod(path_r, mode); }
static int new_fchmodat(int fd, const char *path, mode_t mode, int flags) { RP(path); return orig_fchmodat(fd, path_r, mode, flags); }
static int new_chown(const char *path, uid_t u, gid_t g)  { RP(path); return orig_chown(path_r, u, g); }
static int new_lchown(const char *path, uid_t u, gid_t g) { RP(path); return orig_lchown(path_r, u, g); }
static DIR *new_opendir(const char *path)          { RP(path); return orig_opendir(path_r); }
static int new_chdir(const char *path)             { RP(path); return orig_chdir(path_r); }
static int new_truncate(const char *path, off_t len){ RP(path); return orig_truncate(path_r, len); }

static uid_t new_getuid(void)  { int u = g_fake_uid; return (u >= 0) ? (uid_t) u : orig_getuid(); }
static uid_t new_geteuid(void) { int u = g_fake_uid; return (u >= 0) ? (uid_t) u : orig_geteuid(); }

static int new_kill(pid_t pid, int sig) {
    pid_t me = getpid();
    // Neuter a guest fatally signalling its own process/group -> stops self-restart loops.
    if ((pid == me || pid == 0 || pid == -me) &&
        (sig == SIGKILL || sig == SIGABRT || sig == SIGTERM)) {
        ALOGD("FileSystemHook: neutered self kill(pid=%d,sig=%d)", pid, sig);
        return 0;
    }
    return orig_kill(pid, sig);
}

// ---------------------------------------------------------------- install
#define HOOK_FS(name) do { \
    void *a = xdl_sym(handle, #name, nullptr); \
    if (a) { \
        if (DobbyHook(a, (void *) new_##name, (void **) &orig_##name) == 0) hooked++; \
        else ALOGE("FileSystemHook: DobbyHook failed for %s", #name); \
    } \
} while (0)

void FileSystemHook::setFakeUid(int uid) { g_fake_uid = uid; }

void FileSystemHook::init() {
    ALOGD("FileSystemHook: installing inline libc hooks (redirect + uid + kill + /proc cloak)");
    void *handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) { ALOGE("FileSystemHook: xdl_open(libc.so) failed"); return; }

    int hooked = 0;
    HOOK_FS(openat);      HOOK_FS(openat64);        // openat first: serve_cloaked uses orig_openat
    HOOK_FS(open);        HOOK_FS(open64);
    HOOK_FS(stat);        HOOK_FS(stat64);
    HOOK_FS(lstat);       HOOK_FS(lstat64);
    HOOK_FS(fstatat);     HOOK_FS(fstatat64);   HOOK_FS(newfstatat);
    HOOK_FS(access);      HOOK_FS(faccessat);
    HOOK_FS(readlink);    HOOK_FS(readlinkat);
    HOOK_FS(mkdir);       HOOK_FS(mkdirat);
    HOOK_FS(rmdir);       HOOK_FS(unlink);      HOOK_FS(unlinkat);
    HOOK_FS(rename);      HOOK_FS(renameat);
    HOOK_FS(chmod);       HOOK_FS(fchmodat);
    HOOK_FS(chown);       HOOK_FS(lchown);
    HOOK_FS(opendir);     HOOK_FS(chdir);       HOOK_FS(truncate);
    HOOK_FS(getuid);      HOOK_FS(geteuid);
    HOOK_FS(kill);

    xdl_close(handle);
    ALOGD("FileSystemHook: installed %d inline libc hooks", hooked);
}
