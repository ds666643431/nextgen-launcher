use std::path::{Path, PathBuf};

fn main() {
    // Android 15/16/17 require 16 KB-aligned ELF LOAD segments. NDK r27 still
    // defaults to 4 KB, so force max-page-size on the cdylib link. Additive via
    // rustc-cdylib-link-arg (cargo-ndk's RUSTFLAGS won't clobber it).
    println!("cargo:rustc-cdylib-link-arg=-Wl,-z,max-page-size=16384");

    build_plugins();
}

// The plugin framework — the C++ registry + plugins/register.cpp + every plugin
// TU under plugins/, plus the weak TLS shim. ALWAYS compiled (it is the app's
// extension seam, not an opt-in island). WHICH plugins run is chosen by hand in
// plugins/register.cpp. See docs/plugin-architecture-plan.md.
fn build_plugins() {
    println!("cargo:rerun-if-changed=hardening/src");
    println!("cargo:rerun-if-changed=hardening/include");
    println!("cargo:rerun-if-changed=plugins");

    // (1) C archive: the weak __cxa_thread_atexit shim (freestanding, no
    //     libc++_shared) — kept in its own C TU (can't share -std=c++17).
    cc::Build::new()
        .file("hardening/src/tls_shim.c")
        .flag("-fvisibility=hidden")
        .flag_if_supported("-ffunction-sections")
        .opt_level(2)
        .warnings(false)
        .compile("ng_tls_shim");

    // (2) C++ archive: the obfuscated native detectors (plugins/**/*.cpp — each
    //     exposes an `extern "C"` scan fn called from a Rust plugin via FFI), in ONE
    //     static archive so an unreferenced detector TU is pulled on-demand (gc'd)
    //     and a detector's strong `ng_force_close` overrides the weak no-op only when
    //     that detector is actually referenced (i.e. its plugin is registered).
    let sources = discover_plugin_sources();
    if !sources.is_empty() {
        let mut cpp = cc::Build::new();
        cpp.cpp(true)
            .std("c++17")
            .include("hardening/include")
            .include("plugins")
            // The reveny detector's fork()+PTRACE_ATTACH anti-debug probe stays OFF
            // by default (an untested fork() in the UI process at startup risks
            // stability); its TracerPid fast-path still fires. Remove to re-enable.
            .define("NG_NO_FORK_PTRACE", None)
            .flag("-fno-exceptions")
            .flag("-fno-rtti")
            .flag("-fvisibility=hidden")
            .flag_if_supported("-ffunction-sections")
            .opt_level(2)
            .warnings(false);
        for src in sources {
            cpp.file(src);
        }
        cpp.cpp_link_stdlib(None); // freestanding: do not link/ship libc++_shared.so
        cpp.compile("ng_plugins");
    }
}

// Recursively collect plugins/**/*.cpp (except register.cpp, added explicitly).
fn discover_plugin_sources() -> Vec<PathBuf> {
    let mut out = Vec::new();
    collect_cpp(Path::new("plugins"), &mut out);
    out.retain(|p| p.file_name().map_or(true, |n| n != "register.cpp"));
    out
}

fn collect_cpp(dir: &Path, out: &mut Vec<PathBuf>) {
    let Ok(entries) = std::fs::read_dir(dir) else { return };
    for e in entries.flatten() {
        let p = e.path();
        if p.is_dir() {
            println!("cargo:rerun-if-changed={}", p.display());
            collect_cpp(&p, out);
        } else if p.extension().map_or(false, |x| x == "cpp") {
            println!("cargo:rerun-if-changed={}", p.display());
            out.push(p);
        }
    }
}
