//! RealEngine — JNI bridge to `menu.nextgen.launchera.app.VBridge`, the host-app bridge
//! purpose-built for the native UI (String-only signatures over the NextGen engine).
//! Compiled for `cfg(all(target_os="android", feature="real_engine"))` and only works when
//! libloader is hosted INSIDE the engine app (menu.nextgen.launcher), which bootstraps
//! NextGenCore and contains VBridge.
//!
//! VBridge is an APP class, so `FindClass` from a worker thread (system classloader) can't
//! see it. We resolve it through the Application context's classloader instead.

use jni::objects::{JClass, JObject, JObjectArray, JString, JValue};
use jni::{JNIEnv, JavaVM};

use super::{Accounts, Engine, EngineError, InstallOutcome};

const VBRIDGE: &str = "menu.nextgen.launchera.app.VBridge";
/// VBridge's packed-row field separator (Kotlin `SEP = ""`).
const SEP: char = '\u{1F}';

pub struct RealEngine;

impl RealEngine {
    pub fn new() -> Self {
        RealEngine
    }
}

fn je(e: jni::errors::Error) -> EngineError {
    EngineError(e.to_string())
}

fn call<T>(
    f: impl FnOnce(&mut JNIEnv, &JObject) -> Result<T, jni::errors::Error>,
) -> Result<T, EngineError> {
    let ctx = ndk_context::android_context();
    let vm = unsafe { JavaVM::from_raw(ctx.vm().cast()) }.map_err(je)?;
    let mut env = vm.attach_current_thread_permanently().map_err(je)?;
    let ctx_ptr = ctx.context();
    let r = env.with_local_frame(24, |env| {
        let app = unsafe { JObject::from_raw(ctx_ptr as jni::sys::jobject) };
        f(env, &app)
    });
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_describe();
        let _ = env.exception_clear();
    }
    r.map_err(je)
}

/// Resolve the VBridge class via the Application context's classloader (works from any thread).
fn vbridge<'a>(env: &mut JNIEnv<'a>, app: &JObject) -> Result<JClass<'a>, jni::errors::Error> {
    let cl = env
        .call_method(app, "getClassLoader", "()Ljava/lang/ClassLoader;", &[])?
        .l()?;
    let name = env.new_string(VBRIDGE)?;
    let cls = env
        .call_method(
            &cl,
            "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;",
            &[JValue::Object(&name)],
        )?
        .l()?;
    Ok(JClass::from(cls))
}

fn jstr_to_string(env: &mut JNIEnv, obj: JObject) -> Result<String, jni::errors::Error> {
    let js = JString::from(obj);
    let out: String = env.get_string(&js)?.into();
    Ok(out)
}

/// Read the first field (package name) from each VBridge SEP-packed row.
fn pkgs_from_rows(env: &mut JNIEnv, arr: JObject) -> Result<Vec<String>, jni::errors::Error> {
    let jarr = JObjectArray::from(arr);
    let n = env.get_array_length(&jarr)?;
    let mut out = Vec::with_capacity(n as usize);
    for i in 0..n {
        let el = env.get_object_array_element(&jarr, i)?;
        let row = jstr_to_string(env, el)?;
        if let Some(pkg) = row.split(SEP).next() {
            if !pkg.is_empty() {
                out.push(pkg.to_owned());
            }
        }
    }
    Ok(out)
}

impl Engine for RealEngine {
    fn installed_pkgs(&self, user: i32) -> Result<Vec<String>, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let arr = env
                .call_static_method(&cls, "listCloned", "(I)[Ljava/lang/String;", &[JValue::Int(user)])?
                .l()?;
            pkgs_from_rows(env, arr)
        })
    }

    fn installable_pkgs(&self) -> Result<Vec<String>, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let arr = env
                .call_static_method(&cls, "listInstallable", "()[Ljava/lang/String;", &[])?
                .l()?;
            pkgs_from_rows(env, arr)
        })
    }

    fn is_installed(&self, pkg: &str, user: i32) -> Result<bool, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let p = env.new_string(pkg)?;
            env.call_static_method(
                &cls,
                "isInstalled",
                "(Ljava/lang/String;I)Z",
                &[JValue::Object(&p), JValue::Int(user)],
            )?
            .z()
        })
    }

    fn install_from_device(&self, pkg: &str, user: i32) -> Result<InstallOutcome, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let p = env.new_string(pkg)?;
            // VBridge.install returns null on success, else the error message.
            let res = env
                .call_static_method(
                    &cls,
                    "install",
                    "(Ljava/lang/String;I)Ljava/lang/String;",
                    &[JValue::Object(&p), JValue::Int(user)],
                )?
                .l()?;
            if res.is_null() {
                Ok(InstallOutcome {
                    success: true,
                    package: pkg.to_owned(),
                    msg: String::new(),
                })
            } else {
                Ok(InstallOutcome {
                    success: false,
                    package: pkg.to_owned(),
                    msg: jstr_to_string(env, res)?,
                })
            }
        })
    }

    fn launch(&self, pkg: &str, user: i32) -> Result<bool, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let p = env.new_string(pkg)?;
            // cloneAndLaunch clones-if-needed then launches (the "play" path).
            env.call_static_method(
                &cls,
                "cloneAndLaunch",
                "(Ljava/lang/String;I)Z",
                &[JValue::Object(&p), JValue::Int(user)],
            )?
            .z()
        })
    }

    fn uninstall(&self, pkg: &str, user: i32) -> Result<(), EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            let p = env.new_string(pkg)?;
            env.call_static_method(
                &cls,
                "uninstall",
                "(Ljava/lang/String;I)V",
                &[JValue::Object(&p), JValue::Int(user)],
            )?;
            Ok(())
        })
    }

    fn accounts(&self) -> Result<Accounts, EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            // googleAccount(): String? — null when no Google account is in the container.
            let g = env
                .call_static_method(&cls, "googleAccount", "()Ljava/lang/String;", &[])?
                .l()?;
            let google = if g.is_null() {
                None
            } else {
                Some(jstr_to_string(env, g)?)
            };
            let facebook = env
                .call_static_method(&cls, "facebookSignedIn", "()Z", &[])?
                .z()?;
            Ok(Accounts { google, facebook })
        })
    }

    fn start_google_sign_in(&self) -> Result<(), EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            // startGoogleSignIn(Context) — the Application context is a valid Context.
            env.call_static_method(
                &cls,
                "startGoogleSignIn",
                "(Landroid/content/Context;)V",
                &[JValue::Object(app)],
            )?;
            Ok(())
        })
    }

    fn start_facebook_sign_in(&self) -> Result<(), EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            env.call_static_method(
                &cls,
                "startFacebookSignIn",
                "(Landroid/content/Context;)V",
                &[JValue::Object(app)],
            )?;
            Ok(())
        })
    }

    fn google_sign_out(&self) -> Result<(), EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            env.call_static_method(&cls, "googleSignOut", "()V", &[])?;
            Ok(())
        })
    }

    fn facebook_sign_out(&self) -> Result<(), EngineError> {
        call(|env, app| {
            let cls = vbridge(env, app)?;
            env.call_static_method(&cls, "facebookSignOut", "()V", &[])?;
            Ok(())
        })
    }
}
