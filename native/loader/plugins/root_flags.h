// root_flags.h — the STABLE / FROZEN environment bit map shared by root-detector
// plugins. Whatever root-detector plugin is registered reports a subset of these
// via ng_report_flags(); the server mirrors this map and weights / kill-switches
// per bit without a client rebuild. Keep the values stable.
#ifndef NG_ROOT_FLAGS_H
#define NG_ROOT_FLAGS_H

enum {
    NG_HW_SU_PATH        = 1u << 0,  // su / busybox / Superuser binaries in a PATH dir
    NG_HW_MAGISK         = 1u << 1,  // Magisk fs artifacts / module dir / UDS / logd trick
    NG_HW_KERNELSU       = 1u << 2,  // KernelSU + SuSFS + root-adjacent mod artifacts
    NG_HW_TEST_KEYS      = 1u << 3,  // ro.*.build.tags == "test-keys" / display.id
    NG_HW_DANGEROUS_PROP = 1u << 4,  // ro.debuggable / ro.secure / service.adb.root / adb_root
    NG_HW_SELINUX        = 1u << 5,  // permissive / modified policy files / caller ctx
    NG_HW_MOUNTS         = 1u << 6,  // suspicious/rw mounts (system rw, overlay/tmpfs/bind)
    NG_HW_MAPS_HOOK      = 1u << 7,  // /proc/self/maps signatures / inline hooks / frida
    NG_HW_ANTI_DEBUG     = 1u << 8,  // debugger/tracer attached (TracerPid + fork-ptrace)
    NG_HW_XPOSED         = 1u << 9,  // Xposed / LSPosed
    NG_HW_PROC_ENV       = 1u << 10, // suspicious /proc/self/fd, environ, LineageOS markers
    NG_HW_PROC_MASQ      = 1u << 11, // daemon masquerade (fsck.f2fs) / custom kernel
    NG_HW_MISC_FILES     = 1u << 12, // hosts, install-recovery.sh, /proc/kperfmon, /data/system
};

#endif // NG_ROOT_FLAGS_H
