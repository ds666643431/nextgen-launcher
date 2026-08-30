/* Thin shim so Rust can call ByteDance ShadowHook (android-inline-hook). */
#include "shadowhook.h"

int mm_sh_init(void) {
    /* SHARED mode: robust, coexists with other hooks; needs libshadowhook_nothing.so
       present in the process' library dir (we bundle it). debuggable = false. */
    return shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
}

void *mm_sh_hook_sym_name(const char *lib, const char *sym, void *newfn, void **orig) {
    return shadowhook_hook_sym_name(lib, sym, newfn, orig);
}
