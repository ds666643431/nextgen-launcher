// tls_shim.c — freestanding C++ TLS shim, ALWAYS compiled. A plugin's OBS() →
// AY_OBFUSCATE uses a `thread_local` whose destructor registers via the C++ ABI's
// __cxa_thread_atexit, which normally lives in libc++abi. We build the plugin
// island freestanding (no libc++_shared.so is shipped), so forward that call to
// Bionic's __cxa_thread_atexit_impl (present since API 23; our minSdk is 28).
//
// weak so a real libc++abi definition wins if one is ever linked, with no
// duplicate-symbol conflict; gc'd/harmless in a build whose registered plugins use
// no thread_local, load-bearing for ones that do (e.g. the reveny plugin's OBS()).
extern int __cxa_thread_atexit_impl(void (*func)(void *), void *arg, void *dso);

__attribute__((weak)) int __cxa_thread_atexit(void (*func)(void *), void *arg,
                                              void *dso) {
    return __cxa_thread_atexit_impl(func, arg, dso);
}

// Weak no-op force-close: a plugin's C++ TU (e.g. the reveny detector) may define a
// strong raw-svc kill ladder that overrides this when that plugin is linked in.
__attribute__((weak)) void ng_force_close(void) {}
