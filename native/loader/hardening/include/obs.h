// obs.h — primitive string-obfuscation indirection for the hardening island.
//
// RULE: every sensitive string LITERAL in this island is wrapped in OBS("…").
// OBS is the single swap-point for the obfuscation engine — change the ONE
// #define below to switch engines and every call site follows automatically;
// never touch the call sites.
//
// Today OBS maps to adamyaxley/Obfuscate's AY_OBFUSCATE: a compile-time XOR so
// the plaintext never lands in the binary image (only in a thread-local buffer
// at runtime, which its destructor zeroes at thread exit).
//
// Scope / limits (inherited from AY_OBFUSCATE):
//   * String literals only (char / UTF-8). NOT integers or floats.
//   * OBS(x) yields a char* valid on the calling thread — use it immediately or
//     copy it; do not stash the pointer for another thread.
//   * "Obfuscation, not cryptography": a fixed compile-time XOR. Never use it for
//     real payloads — those use AEAD (see docs/architecture.md).
#ifndef NG_OBS_H
#define NG_OBS_H

#include "obfuscate.h"

// ── the one line to change when the obfuscation engine changes ──────────────
#define OBS(str) AY_OBFUSCATE(str)

#endif // NG_OBS_H
