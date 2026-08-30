use std::env;

fn main() {
    let dir = env::var("CARGO_MANIFEST_DIR").unwrap();
    let target = env::var("TARGET").unwrap_or_default();
    let abi = if target.starts_with("aarch64") {
        "arm64-v8a"
    } else if target.starts_with("x86_64") {
        "x86_64"
    } else if target.starts_with("armv7") || target.starts_with("thumbv7") {
        "armeabi-v7a"
    } else {
        "arm64-v8a"
    };

    // 16 KB ELF alignment (A15/16/17).
    println!("cargo:rustc-cdylib-link-arg=-Wl,-z,max-page-size=16384");

    // Compile the ShadowHook shim with the NDK compiler (cargo-ndk sets CC).
    cc::Build::new()
        .file("src/shadowhook_shim.c")
        .include(format!("{dir}/vendor/shadowhook"))
        .warnings(false)
        .compile("shadowhook_shim");

    // Link ByteDance ShadowHook (android-inline-hook). It is a self-contained
    // shared lib loaded at runtime from the guest's nativeLibraryDir alongside
    // libmodmenu.so (we bundle libshadowhook.so + libshadowhook_nothing.so there).
    println!("cargo:rustc-link-search=native={dir}/vendor/shadowhook/{abi}");
    println!("cargo:rustc-link-lib=dylib=shadowhook");

    println!("cargo:rustc-link-lib=log");
    println!("cargo:rustc-link-lib=dl");
}
