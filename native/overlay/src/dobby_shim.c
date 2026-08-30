/* Thin shim so Rust can call Dobby. dobby.h #defines DobbyHook to an obfuscated
 * symbol; including the header here makes the macro expand to the real symbol
 * exported by libdobby.a. */
#include "dobby.h"

int mm_dobby_hook(void *addr, void *replace, void **orig) {
    return DobbyHook(addr, replace, orig);
}

void *mm_dobby_resolve(const char *image_name, const char *symbol_name) {
    return DobbySymbolResolver(image_name, symbol_name);
}
