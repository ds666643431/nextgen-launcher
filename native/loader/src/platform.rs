//! Platform glue that is plain Android (no Bcore): the All-Files-Access permission gate
//! that the onboarding screen drives, and opening a URL. Android impl is JNI; the desktop
//! /fake impl is an in-process toggle so onboarding is demoable without a device.

/// Is MANAGE_EXTERNAL_STORAGE ("All files access") granted? On API < 30 there is no such
/// concept, so we treat it as granted.
pub fn all_files_access_granted() -> bool {
    imp::all_files_access_granted()
}

/// Open the system "All files access" settings page for this app.
pub fn request_all_files_access() {
    imp::request_all_files_access()
}

/// Open a URL / deep link (Telegram, Play Store, …) in the system handler.
pub fn open_url(url: &str) {
    imp::open_url(url)
}

/// Real display safe-area insets in **pixels** `[top, left, right, bottom]` (camera cutout
/// ∪ system bars), or `None` when unavailable (pre-30, no window yet, or the JNI context is
/// not an Activity) — callers keep their fallback in that case.
pub fn safe_insets_px() -> Option<[f32; 4]> {
    imp::safe_insets_px()
}

// ---------------------------------------------------------------- Android (JNI) --------
#[cfg(target_os = "android")]
mod imp {
    use jni::objects::{JObject, JString, JValue};
    use jni::{JNIEnv, JavaVM};

    const FLAG_ACTIVITY_NEW_TASK: i32 = 0x1000_0000;

    fn with<T>(f: impl FnOnce(&mut JNIEnv, &JObject) -> Result<T, jni::errors::Error>) -> Option<T> {
        let ctx = ndk_context::android_context();
        let vm = unsafe { JavaVM::from_raw(ctx.vm().cast()) }.ok()?;
        let mut env = vm.attach_current_thread_permanently().ok()?;
        let ctx_ptr = ctx.context();
        let out = env.with_local_frame(16, |env| {
            let app = unsafe { JObject::from_raw(ctx_ptr as jni::sys::jobject) };
            f(env, &app)
        });
        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_describe();
            let _ = env.exception_clear();
        }
        match out {
            Ok(v) => Some(v),
            Err(e) => {
                log::warn!("[platform] jni error: {e}");
                None
            }
        }
    }

    fn sdk_int(env: &mut JNIEnv) -> i32 {
        env.get_static_field("android/os/Build$VERSION", "SDK_INT", "I")
            .and_then(|v| v.i())
            .unwrap_or(30)
    }

    /// Like `with`, but swallows exceptions silently — used for the per-tick insets probe so
    /// a context that never yields a window doesn't spam the log every refresh.
    fn with_quiet<T>(
        f: impl FnOnce(&mut JNIEnv, &JObject) -> Result<T, jni::errors::Error>,
    ) -> Option<T> {
        let ctx = ndk_context::android_context();
        let vm = unsafe { JavaVM::from_raw(ctx.vm().cast()) }.ok()?;
        let mut env = vm.attach_current_thread_permanently().ok()?;
        let ctx_ptr = ctx.context();
        let out = env.with_local_frame(24, |env: &mut JNIEnv| {
            let app = unsafe { JObject::from_raw(ctx_ptr as jni::sys::jobject) };
            f(env, &app)
        });
        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_clear();
        }
        out.ok()
    }

    pub fn safe_insets_px() -> Option<[f32; 4]> {
        with_quiet(|env, app| -> Result<Option<[f32; 4]>, jni::errors::Error> {
            let sdk = sdk_int(env);
            if sdk < 28 {
                return Ok(None);
            }
            // activity.getWindow().getDecorView().getRootWindowInsets()
            let window = env
                .call_method(app, "getWindow", "()Landroid/view/Window;", &[])?
                .l()?;
            if window.is_null() {
                return Ok(None);
            }
            let decor = env
                .call_method(&window, "getDecorView", "()Landroid/view/View;", &[])?
                .l()?;
            if decor.is_null() {
                return Ok(None);
            }
            let wi = env
                .call_method(&decor, "getRootWindowInsets", "()Landroid/view/WindowInsets;", &[])?
                .l()?;
            if wi.is_null() {
                return Ok(None);
            }

            if sdk >= 30 {
                // getInsets(Type.systemBars() | Type.displayCutout())
                let bars = env
                    .call_static_method("android/view/WindowInsets$Type", "systemBars", "()I", &[])?
                    .i()?;
                let cutout = env
                    .call_static_method(
                        "android/view/WindowInsets$Type",
                        "displayCutout",
                        "()I",
                        &[],
                    )?
                    .i()?;
                let ins = env
                    .call_method(
                        &wi,
                        "getInsets",
                        "(I)Landroid/graphics/Insets;",
                        &[JValue::Int(bars | cutout)],
                    )?
                    .l()?;
                if ins.is_null() {
                    return Ok(None);
                }
                let top = env.get_field(&ins, "top", "I")?.i()? as f32;
                let left = env.get_field(&ins, "left", "I")?.i()? as f32;
                let right = env.get_field(&ins, "right", "I")?.i()? as f32;
                let bottom = env.get_field(&ins, "bottom", "I")?.i()? as f32;
                Ok(Some([top, left, right, bottom]))
            } else {
                // API 28-29: union of the system-window insets and the cutout safe insets.
                let get = |env: &mut JNIEnv, name: &str| -> Result<i32, jni::errors::Error> {
                    env.call_method(&wi, name, "()I", &[])?.i()
                };
                let mut top = get(env, "getSystemWindowInsetTop")?;
                let mut left = get(env, "getSystemWindowInsetLeft")?;
                let mut right = get(env, "getSystemWindowInsetRight")?;
                let mut bottom = get(env, "getSystemWindowInsetBottom")?;
                let dc = env
                    .call_method(&wi, "getDisplayCutout", "()Landroid/view/DisplayCutout;", &[])?
                    .l()?;
                if !dc.is_null() {
                    let gc = |env: &mut JNIEnv, name: &str| -> Result<i32, jni::errors::Error> {
                        env.call_method(&dc, name, "()I", &[])?.i()
                    };
                    top = top.max(gc(env, "getSafeInsetTop")?);
                    left = left.max(gc(env, "getSafeInsetLeft")?);
                    right = right.max(gc(env, "getSafeInsetRight")?);
                    bottom = bottom.max(gc(env, "getSafeInsetBottom")?);
                }
                Ok(Some([top as f32, left as f32, right as f32, bottom as f32]))
            }
        })
        .flatten()
    }

    pub fn all_files_access_granted() -> bool {
        with(|env, _app| {
            if sdk_int(env) < 30 {
                return Ok(true); // no All-Files-Access concept pre-API-30
            }
            env.call_static_method(
                "android/os/Environment",
                "isExternalStorageManager",
                "()Z",
                &[],
            )?
            .z()
        })
        .unwrap_or(false)
    }

    /// Open the "All files access" settings page for **this** app.
    ///
    /// The launcher runs inside the engine host (`menu.nextgen.launcher`), not the `app/`
    /// loader id — so the target package MUST be read at runtime via `getPackageName()`.
    /// A hardcoded id deep-links the toggle to a package that isn't installed, so granting
    /// there never affects the running app and onboarding never advances (this was the
    /// "grant not working" bug on both MuMu and real devices).
    ///
    /// Some ROMs / emulators don't expose the per-app deep-link page, so we fall back from
    /// the per-app page → the global All-Files-Access list → the app-details page, firing
    /// the first candidate that a Settings activity actually handles.
    pub fn request_all_files_access() {
        with(|env, app| {
            if sdk_int(env) < 30 {
                return Ok(());
            }
            let pkg = package_name(env, app)?;
            // (settings action, attach `package:<pkg>` data?) — best UX first.
            let candidates: [(&str, bool); 3] = [
                (
                    "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    true,
                ),
                ("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION", false),
                ("android.settings.APPLICATION_DETAILS_SETTINGS", true),
            ];
            for (action, with_data) in candidates {
                let data = if with_data { Some(pkg.as_str()) } else { None };
                if try_start_settings(env, app, action, data)? {
                    return Ok(());
                }
            }
            log::warn!("[platform] no Settings activity handled the all-files-access intent");
            Ok(())
        });
    }

    /// The running app's package id (`context.getPackageName()`), read at runtime.
    fn package_name(env: &mut JNIEnv, app: &JObject) -> Result<String, jni::errors::Error> {
        let obj = env
            .call_method(app, "getPackageName", "()Ljava/lang/String;", &[])?
            .l()?;
        let js = JString::from(obj);
        let out: String = env.get_string(&js)?.into();
        Ok(out)
    }

    /// Build `Intent(action[, "package:<pkg>"])`, add `NEW_TASK`, and `startActivity`.
    /// Returns `Ok(true)` if it launched, `Ok(false)` if no activity handled it (an
    /// `ActivityNotFoundException` is caught + cleared so the caller can try the next
    /// candidate). Each attempt runs in its own local frame so refs don't accumulate.
    fn try_start_settings(
        env: &mut JNIEnv,
        app: &JObject,
        action: &str,
        data_pkg: Option<&str>,
    ) -> Result<bool, jni::errors::Error> {
        env.with_local_frame(12, |env| {
            let action = env.new_string(action)?;
            let intent = match data_pkg {
                Some(pkg) => {
                    let uri_str = env.new_string(format!("package:{pkg}"))?;
                    let uri = env
                        .call_static_method(
                            "android/net/Uri",
                            "parse",
                            "(Ljava/lang/String;)Landroid/net/Uri;",
                            &[JValue::Object(&uri_str)],
                        )?
                        .l()?;
                    env.new_object(
                        "android/content/Intent",
                        "(Ljava/lang/String;Landroid/net/Uri;)V",
                        &[JValue::Object(&action), JValue::Object(&uri)],
                    )?
                }
                None => env.new_object(
                    "android/content/Intent",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&action)],
                )?,
            };
            env.call_method(
                &intent,
                "addFlags",
                "(I)Landroid/content/Intent;",
                &[JValue::Int(FLAG_ACTIVITY_NEW_TASK)],
            )?;
            match env.call_method(
                app,
                "startActivity",
                "(Landroid/content/Intent;)V",
                &[JValue::Object(&intent)],
            ) {
                Ok(_) => Ok(true),
                Err(jni::errors::Error::JavaException) => {
                    // No activity handled it (e.g. ROM without the per-app page).
                    // Clear the pending exception so the next candidate can run.
                    let _ = env.exception_clear();
                    Ok(false)
                }
                Err(e) => Err(e),
            }
        })
    }

    pub fn open_url(url: &str) {
        let url = url.to_owned();
        with(|env, app| {
            let action = env.new_string("android.intent.action.VIEW")?;
            let uri_str = env.new_string(&url)?;
            let uri = env
                .call_static_method(
                    "android/net/Uri",
                    "parse",
                    "(Ljava/lang/String;)Landroid/net/Uri;",
                    &[JValue::Object(&uri_str)],
                )?
                .l()?;
            let intent = env.new_object(
                "android/content/Intent",
                "(Ljava/lang/String;Landroid/net/Uri;)V",
                &[JValue::Object(&action), JValue::Object(&uri)],
            )?;
            env.call_method(
                &intent,
                "addFlags",
                "(I)Landroid/content/Intent;",
                &[JValue::Int(FLAG_ACTIVITY_NEW_TASK)],
            )?;
            env.call_method(
                app,
                "startActivity",
                "(Landroid/content/Intent;)V",
                &[JValue::Object(&intent)],
            )?;
            Ok(())
        });
    }
}

// ---------------------------------------------------------------- Desktop / fake -------
#[cfg(not(target_os = "android"))]
mod imp {
    use std::sync::atomic::{AtomicBool, Ordering};
    static GRANTED: AtomicBool = AtomicBool::new(false);

    pub fn all_files_access_granted() -> bool {
        GRANTED.load(Ordering::Relaxed)
    }
    pub fn request_all_files_access() {
        // Simulate the user granting it, so onboarding advances in dev.
        GRANTED.store(true, Ordering::Relaxed);
    }
    pub fn open_url(url: &str) {
        log::info!("[platform] would open {url}");
    }
    pub fn safe_insets_px() -> Option<[f32; 4]> {
        None // desktop has no cutout; the UI falls back to its constant
    }
}
