# hardening/ — commercial hardening island (loader)

A single feature-gated C/C++ island compiled into `libloader.so`, holding the
project's **primitive string obfuscation** and (later) **native root detection**.
See `docs/hardening-plan.md` for the full design; this is the code that exists.

## What is implemented (hardening-plan M-H1 + M-H2)

- **Multi-language enabler** — a C (`root_detect.c`) + C++ (`obf_secrets.cpp`)
  island linked into the Rust cdylib, behind one `extern "C"` surface:
  `int ng_hardening_check(void)` (bitmask; `0` == clean).
- **Primitive string obfuscation** — every sensitive literal is wrapped in
  `OBS("…")` (see `include/obs.h`), which today maps to **adamyaxley/Obfuscate**
  (`AY_OBFUSCATE`, compile-time XOR). Plaintext never lands in the binary.

Root detection is deliberately minimal for now (a few obfuscated su/Magisk path
probes, just enough to exercise the obfuscation end-to-end). The full detector
suite is **M-H3**; logic obfuscation (O-MVLL) is **M-H4**.

## The `OBS()` swap-point

`include/obs.h` is the only place the obfuscation engine is named:

```c
#define OBS(str) AY_OBFUSCATE(str)   // change this ONE line to swap engines
```

Change engines by editing that single `#define` — call sites never change.

## Removal seam

Everything here is gated behind the `commercial` Cargo feature (default **OFF**).
A public build (`cargo ndk … build`, no `--features commercial`) compiles zero
C/C++, resolves no `cc` build-dep, and `ng_hardening_check` is absent from
`libloader.so`; the Rust side falls back to a const-`0` `scan()`.

Per the plan this directory should ultimately live in a **private submodule** (or
be `.gitignore`d in any public fork) so the OSS tree ships nothing commercial.

## Build notes / gotchas

- Compiled at **`-O2`**, freestanding: `-fno-exceptions -fno-rtti`,
  `-fvisibility=hidden`, **no** `libc++_shared.so` shipped. AY_OBFUSCATE's
  `thread_local` destructor needs `__cxa_thread_atexit`; a weak shim in
  `root_detect.c` forwards it to Bionic's `__cxa_thread_atexit_impl` (API ≥ 23).
- `OBS()` is **string-literals only** (AY_OBFUSCATE's limit) — not ints/floats.
- `include/obfuscate.h` is vendored **verbatim** (Public Domain / Unlicense — the
  license text is at the foot of the header). Keep it pristine so it stays easy
  to update or replace.

## Verify

```
# obfuscation worked — no plaintext markers in the .so:
llvm-strings libloader.so | grep -iE 'magisk|xbin/su|/data/adb'   # → empty
# linked in, but not exported past the .a (hidden visibility):
llvm-nm libloader.so | grep ng_hardening_check                    # → local 't'
llvm-nm -D libloader.so | grep ng_hardening_check                 # → empty
# 16 KB ELF alignment intact:
llvm-readelf -l libloader.so | grep LOAD                          # align 0x4000
```
