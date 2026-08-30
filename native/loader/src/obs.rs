//! Rust-side primitive-obfuscation seam — the Rust twin of the C++ `OBS()`/Obfuscate.
//!
//! Wrap sensitive Rust *literals* so their plaintext is not visible in
//! `libloader.so` to `strings`/IDA. Backed by the **obfstr** crate (compile-time
//! XOR, deobfuscated onto the stack at the use-site) — the Rust equivalent of
//! adamyaxley/Obfuscate. rustc's LLVM can't be touched by the C++ obfuscators, so
//! Rust literals need this Rust-native path.
//!
//! SINGLE SWAP-POINT: to change the Rust obfuscation engine, edit ONLY the macro
//! bodies here (mirrors `hardening/include/obs.h` on the C++ side).
//!
//! ALWAYS ON: `obfstr` is a non-optional dep and these macros always obfuscate —
//! sensitive Rust literals should never ship in plaintext, and obfstr is a
//! zero-runtime-cost compile-time XOR. (The `commercial` feature that used to gate
//! this is gone; obfuscation is no longer tied to any build tier.)
//!
//! USE IN-STATEMENT: like obfstr (and Obfuscate) the value borrows a stack buffer,
//! so consume it in the same statement — `.into()`, `.to_owned()`, or pass it as a
//! `&str`/`&[u8]` argument. Do NOT bind it to a `'static` (use `obs_string!` when
//! you need an owned `String`).

#![allow(unused_macros)]

/// Obfuscate a string literal → `&str`. The Rust twin of `OBS("...")`.
macro_rules! obs { ($s:expr) => { ::obfstr::obfstr!($s) }; }

/// Obfuscate a byte-string / byte-array literal → `&[u8]` — for keys, magic bytes,
/// the `Start()` secret, and other non-text primitives.
macro_rules! obs_bytes { ($b:expr) => { ::obfstr::obfbytes!($b) }; }

/// Obfuscate a string literal → owned `String`, when consuming a borrowed
/// temporary in-statement is awkward.
macro_rules! obs_string { ($s:expr) => { ::obfstr::obfstring!($s) }; }
