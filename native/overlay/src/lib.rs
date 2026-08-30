//! nextgenlauncher mod .so — injected into the guest game process.
//! - proves in-process access + Dobby hooking of the game's libil2cpp.so
//! - renders a simple egui overlay menu into an injected Android SurfaceView

use std::collections::BTreeSet;
use std::ffi::{c_void, CStr, CString};
use std::os::raw::{c_char, c_int};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

use jni::sys::{jclass, jfloat, jint, jobject, JNIEnv as SysJNIEnv};

// ---- Encrypted traceability logger: logcat + ChaCha20-encrypted file -------
use chacha20::cipher::{KeyIvInit, StreamCipher};
use chacha20::ChaCha20;

extern "C" {
    fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

struct EncWriter {
    file: std::fs::File,
    cipher: ChaCha20,
}
impl EncWriter {
    fn new(path: &str) -> Option<Self> {
        let file = std::fs::OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(path)
            .ok()?;
        // Fixed key/nonce so traces can be decrypted (see scripts/decrypt_trace.py).
        let key: [u8; 32] = *b"nextgenlauncher-trace-key-2026-A";
        let nonce: [u8; 12] = *b"ngl-trace001";
        Some(Self {
            file,
            cipher: ChaCha20::new(&key.into(), &nonce.into()),
        })
    }
    fn write(&mut self, line: &[u8]) {
        use std::io::Write;
        let mut buf = line.to_vec();
        self.cipher.apply_keystream(&mut buf);
        let _ = self.file.write_all(&buf);
        let _ = self.file.flush();
    }
}

struct DualLogger {
    enc: Mutex<Option<EncWriter>>,
    path: String,
}
impl log::Log for DualLogger {
    fn enabled(&self, m: &log::Metadata) -> bool {
        // Trace everything of ours; drop wgpu/naga Debug/Trace per-frame spam.
        if m.level() > log::Level::Info
            && (m.target().starts_with("wgpu") || m.target().starts_with("naga"))
        {
            return false;
        }
        true
    }
    fn log(&self, record: &log::Record) {
        let line = format!("[{}] {}: {}\n", record.level(), record.target(), record.args());
        let prio = match record.level() {
            log::Level::Error => 6,
            log::Level::Warn => 5,
            log::Level::Info => 4,
            log::Level::Debug => 3,
            log::Level::Trace => 2,
        };
        if let Ok(msg) = CString::new(line.trim_end()) {
            let tag = CString::new("MODMENU").unwrap();
            unsafe { __android_log_write(prio, tag.as_ptr(), msg.as_ptr()) };
        }
        if let Some(w) = self.enc.lock().unwrap().as_mut() {
            w.write(line.as_bytes());
        }
    }
    fn flush(&self) {}
}

static LOGGER: OnceLock<DualLogger> = OnceLock::new();

fn init_log() {
    let logger = LOGGER.get_or_init(|| {
        let dir = std::env::var("MODMENU_LOG_DIR").unwrap_or_else(|_| "/data/local/tmp".to_owned());
        let path = format!("{dir}/nextgenlauncher_trace.enc");
        let enc = EncWriter::new(&path);
        DualLogger { enc: Mutex::new(enc), path }
    });
    if log::set_logger(logger).is_ok() {
        log::set_max_level(log::LevelFilter::Trace);
    }
    let tag = CString::new("MODMENU").unwrap();
    let m = CString::new(format!("encrypted trace -> {}", logger.path)).unwrap();
    unsafe { __android_log_write(4, tag.as_ptr(), m.as_ptr()) };
}

// ========================= ShadowHook inline-hook demo ======================
extern "C" {
    fn mm_sh_init() -> c_int;
    fn mm_sh_hook_sym_name(
        lib: *const c_char,
        sym: *const c_char,
        newfn: *mut c_void,
        orig: *mut *mut c_void,
    ) -> *mut c_void;
}

static HOOK_CALLS: AtomicUsize = AtomicUsize::new(0);
type StringNewFn = unsafe extern "C" fn(*const c_char) -> *mut c_void;
static mut ORIG_STRING_NEW: Option<StringNewFn> = None;

unsafe extern "C" fn hk_il2cpp_string_new(s: *const c_char) -> *mut c_void {
    HOOK_CALLS.fetch_add(1, Ordering::Relaxed);
    match ORIG_STRING_NEW {
        Some(f) => f(s),
        None => std::ptr::null_mut(),
    }
}

fn log_visible_game_libs() {
    if let Ok(maps) = std::fs::read_to_string("/proc/self/maps") {
        let mut libs = BTreeSet::new();
        for line in maps.lines() {
            if let Some(path) = line.split_whitespace().last() {
                if path.ends_with(".so")
                    && (path.contains("il2cpp") || path.contains("unity")
                        || path.contains("libmain") || path.contains("pairip"))
                {
                    libs.insert(path.to_owned());
                }
            }
        }
        for l in &libs {
            log::info!("[maps] game lib: {l}");
        }
    }
}

fn lib_loaded_exec(substr: &str) -> bool {
    if let Ok(maps) = std::fs::read_to_string("/proc/self/maps") {
        for line in maps.lines() {
            if line.contains(substr) {
                if let Some(perms) = line.split_whitespace().nth(1) {
                    if perms.contains('x') {
                        return true;
                    }
                }
            }
        }
    }
    false
}

fn hook_worker() {
    // Overlay-only mode: skip the inline il2cpp hook entirely when the loader sets
    // MODMENU_HOOK=0 (the code patch is what CodeStage ACTk flags as "Cheater").
    if std::env::var("MODMENU_HOOK").map(|v| v == "0").unwrap_or(false) {
        log::warn!("[hook] MODMENU_HOOK=0 -> overlay-only, no il2cpp hook");
        log_visible_game_libs();
        return;
    }
    // ByteDance ShadowHook: one-time init (SHARED mode).
    let ir = unsafe { mm_sh_init() };
    log::info!("[ShadowHook] init rc={ir} (0=ok)");

    // Wait until libil2cpp.so is loaded + executable, then settle so il2cpp / PairIP
    // finish their own setup before we patch a code page.
    let mut ready = false;
    for _ in 0..600 {
        if lib_loaded_exec("libil2cpp.so") {
            ready = true;
            break;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    log_visible_game_libs();
    if !ready {
        log::warn!("[ShadowHook] libil2cpp.so not present; skip hook");
        return;
    }
    std::thread::sleep(Duration::from_millis(3000));

    // ShadowHook resolves + hooks by symbol name in a single call.
    let lib = CString::new("libil2cpp.so").unwrap();
    let sym = CString::new("il2cpp_string_new").unwrap();
    let stub = unsafe {
        mm_sh_hook_sym_name(
            lib.as_ptr(),
            sym.as_ptr(),
            hk_il2cpp_string_new as *mut c_void,
            std::ptr::addr_of_mut!(ORIG_STRING_NEW) as *mut *mut c_void,
        )
    };
    if stub.is_null() {
        log::warn!("[ShadowHook] hook il2cpp_string_new FAILED (null stub)");
    } else {
        log::info!("[ShadowHook] hook il2cpp_string_new OK stub={stub:p}");
    }
}

// ============================ Overlay (egui) ================================
enum RenderMsg {
    Surface(usize, i32, i32), // ANativeWindow ptr, width, height
    Destroyed,
}

static RENDER_TX: OnceLock<Mutex<Option<Sender<RenderMsg>>>> = OnceLock::new();
static TOUCHES: OnceLock<Mutex<Vec<(i32, f32, f32)>>> = OnceLock::new();

fn render_tx() -> &'static Mutex<Option<Sender<RenderMsg>>> {
    RENDER_TX.get_or_init(|| Mutex::new(None))
}
fn touches() -> &'static Mutex<Vec<(i32, f32, f32)>> {
    TOUCHES.get_or_init(|| Mutex::new(Vec::new()))
}

fn ensure_render_thread() {
    let mut g = render_tx().lock().unwrap();
    if g.is_none() {
        let (tx, rx) = channel();
        *g = Some(tx);
        std::thread::Builder::new()
            .name("modmenu-egui".into())
            .spawn(move || render_loop(rx))
            .ok();
    }
}

struct Menu {
    god_mode: bool,
    infinite_gold: bool,
    speed: f32,
    open: bool,
}
impl Default for Menu {
    fn default() -> Self {
        Self { god_mode: false, infinite_gold: false, speed: 1.0, open: true }
    }
}

// ===================== GL (egui_glow) backend =====================
// wgpu's GLES backend stalls in queue.submit on MuMu's emulated GPU ("Waiting on last
// submission"). egui_glow issues immediate glDraw calls against our own EGL context on
// the overlay SurfaceView, so there is no fence to hang; it renders on emulated GL and
// on real devices alike.
use egui_glow::glow;
use egui_glow::glow::HasContext;

type EGLDisplay = *mut c_void;
type EGLConfig = *mut c_void;
type EGLContext = *mut c_void;
type EGLSurface = *mut c_void;
#[link(name = "EGL")]
extern "C" {
    fn eglGetDisplay(display_id: *mut c_void) -> EGLDisplay;
    fn eglInitialize(dpy: EGLDisplay, major: *mut i32, minor: *mut i32) -> u32;
    fn eglBindAPI(api: u32) -> u32;
    fn eglChooseConfig(dpy: EGLDisplay, attrib_list: *const i32, configs: *mut EGLConfig, config_size: i32, num_config: *mut i32) -> u32;
    fn eglCreateContext(dpy: EGLDisplay, config: EGLConfig, share: EGLContext, attrib_list: *const i32) -> EGLContext;
    fn eglCreateWindowSurface(dpy: EGLDisplay, config: EGLConfig, win: *mut c_void, attrib_list: *const i32) -> EGLSurface;
    fn eglMakeCurrent(dpy: EGLDisplay, draw: EGLSurface, read: EGLSurface, ctx: EGLContext) -> u32;
    fn eglSwapBuffers(dpy: EGLDisplay, surface: EGLSurface) -> u32;
    fn eglGetError() -> i32;
    fn eglQuerySurface(dpy: EGLDisplay, surface: EGLSurface, attribute: i32, value: *mut i32) -> u32;
    fn eglDestroySurface(dpy: EGLDisplay, surface: EGLSurface) -> u32;
    fn eglDestroyContext(dpy: EGLDisplay, ctx: EGLContext) -> u32;
}
const EGL_NO_CONTEXT: EGLContext = std::ptr::null_mut();
const EGL_NO_SURFACE: EGLSurface = std::ptr::null_mut();
const EGL_NO_DISPLAY: EGLDisplay = std::ptr::null_mut();
const EGL_OPENGL_ES_API: u32 = 0x30A0;
const EGL_RENDERABLE_TYPE: i32 = 0x3040;
const EGL_OPENGL_ES2_BIT: i32 = 0x0004;
const EGL_SURFACE_TYPE: i32 = 0x3033;
const EGL_WINDOW_BIT: i32 = 0x0004;
const EGL_RED_SIZE: i32 = 0x3024;
const EGL_GREEN_SIZE: i32 = 0x3023;
const EGL_BLUE_SIZE: i32 = 0x3022;
const EGL_ALPHA_SIZE: i32 = 0x3021;
const EGL_DEPTH_SIZE: i32 = 0x3025;
const EGL_NONE: i32 = 0x3038;
const EGL_CONTEXT_CLIENT_VERSION: i32 = 0x3098;
const EGL_WIDTH: i32 = 0x3057;
const EGL_HEIGHT: i32 = 0x3056;

struct Egl {
    display: EGLDisplay,
    surface: EGLSurface,
    context: EGLContext,
}

struct Gpu {
    egl: Egl,
    gl: std::sync::Arc<glow::Context>,
    painter: egui_glow::Painter,
    egui_ctx: egui::Context,
    ppp: f32,
    menu: Menu,
    start: Instant,
    w: i32,
    h: i32,
}

impl Drop for Gpu {
    fn drop(&mut self) {
        unsafe {
            if eglMakeCurrent(self.egl.display, self.egl.surface, self.egl.surface, self.egl.context) != 0 {
                self.painter.destroy();
            }
            eglMakeCurrent(self.egl.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface(self.egl.display, self.egl.surface);
            eglDestroyContext(self.egl.display, self.egl.context);
        }
    }
}

const PPP: f32 = 2.75;

type EglGetProc = unsafe extern "C" fn(*const c_char) -> *const c_void;
// Load core GLES via dlsym (eglGetProcAddress returns NULL for core entry points on many
// Android EGL impls), falling back to eglGetProcAddress for extensions. (From eden-mod.)
unsafe fn make_glow() -> Option<glow::Context> {
    let v3 = libc::dlopen(b"libGLESv3.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_NOLOAD);
    let v2 = libc::dlopen(b"libGLESv2.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_NOLOAD);
    let egl = libc::dlopen(b"libEGL.so\0".as_ptr() as *const c_char, libc::RTLD_NOW);
    let egl_get_proc: Option<EglGetProc> = if egl.is_null() {
        None
    } else {
        let pp = libc::dlsym(egl, b"eglGetProcAddress\0".as_ptr() as *const c_char);
        if pp.is_null() { None } else { Some(std::mem::transmute::<*mut c_void, EglGetProc>(pp)) }
    };
    if v3.is_null() && v2.is_null() && egl_get_proc.is_none() {
        return None;
    }
    Some(glow::Context::from_loader_function_cstr(move |name: &CStr| {
        for h in [v3, v2] {
            if !h.is_null() {
                let pp = libc::dlsym(h, name.as_ptr());
                if !pp.is_null() {
                    return pp as *const c_void;
                }
            }
        }
        if let Some(f) = egl_get_proc {
            let pp = f(name.as_ptr());
            if !pp.is_null() {
                return pp;
            }
        }
        std::ptr::null()
    }))
}

fn create_gpu(anw: usize, w: i32, h: i32) -> Option<Gpu> {
    unsafe {
        let display = eglGetDisplay(std::ptr::null_mut());
        if display == EGL_NO_DISPLAY {
            log::error!("[overlay] eglGetDisplay failed");
            return None;
        }
        let (mut maj, mut min) = (0i32, 0i32);
        if eglInitialize(display, &mut maj, &mut min) == 0 {
            log::error!("[overlay] eglInitialize failed 0x{:x}", eglGetError());
            return None;
        }
        eglBindAPI(EGL_OPENGL_ES_API);
        let cfg_attrs = [
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 0,
            EGL_NONE,
        ];
        let mut config: EGLConfig = std::ptr::null_mut();
        let mut num = 0i32;
        if eglChooseConfig(display, cfg_attrs.as_ptr(), &mut config, 1, &mut num) == 0 || num < 1 {
            log::error!("[overlay] eglChooseConfig failed 0x{:x}", eglGetError());
            return None;
        }
        let ctx3 = [EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE];
        let mut context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctx3.as_ptr());
        if context == EGL_NO_CONTEXT {
            let ctx2 = [EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE];
            context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctx2.as_ptr());
        }
        if context == EGL_NO_CONTEXT {
            log::error!("[overlay] eglCreateContext failed 0x{:x}", eglGetError());
            return None;
        }
        let surface = eglCreateWindowSurface(display, config, anw as *mut c_void, std::ptr::null());
        if surface == EGL_NO_SURFACE {
            log::error!("[overlay] eglCreateWindowSurface failed 0x{:x}", eglGetError());
            eglDestroyContext(display, context);
            return None;
        }
        if eglMakeCurrent(display, surface, surface, context) == 0 {
            log::error!("[overlay] eglMakeCurrent failed 0x{:x}", eglGetError());
            eglDestroySurface(display, surface);
            eglDestroyContext(display, context);
            return None;
        }
        let (mut sw, mut sh) = (w.max(1), h.max(1));
        let mut qv = 0i32;
        if eglQuerySurface(display, surface, EGL_WIDTH, &mut qv) != 0 && qv > 0 { sw = qv; }
        if eglQuerySurface(display, surface, EGL_HEIGHT, &mut qv) != 0 && qv > 0 { sh = qv; }

        let gl = match make_glow() {
            Some(g) => std::sync::Arc::new(g),
            None => {
                log::error!("[overlay] make_glow failed");
                eglDestroySurface(display, surface);
                eglDestroyContext(display, context);
                return None;
            }
        };
        let painter = match egui_glow::Painter::new(gl.clone(), "", None, false) {
            Ok(p) => p,
            Err(e) => {
                log::error!("[overlay] egui_glow Painter::new failed: {e}");
                eglDestroySurface(display, surface);
                eglDestroyContext(display, context);
                return None;
            }
        };
        Some(Gpu {
            egl: Egl { display, surface, context },
            gl,
            painter,
            egui_ctx: egui::Context::default(),
            ppp: PPP,
            menu: Menu::default(),
            start: Instant::now(),
            w: sw,
            h: sh,
        })
    }
}

fn drain_touch_events(ppp: f32) -> Vec<egui::Event> {
    let mut out = Vec::new();
    let mut q = touches().lock().unwrap();
    for (action, x, y) in q.drain(..) {
        let pos = egui::pos2(x / ppp, y / ppp);
        match action {
            0 => {
                // ACTION_DOWN
                out.push(egui::Event::PointerMoved(pos));
                out.push(egui::Event::PointerButton {
                    pos,
                    button: egui::PointerButton::Primary,
                    pressed: true,
                    modifiers: egui::Modifiers::default(),
                });
            }
            1 | 3 => {
                // UP / CANCEL
                out.push(egui::Event::PointerButton {
                    pos,
                    button: egui::PointerButton::Primary,
                    pressed: false,
                    modifiers: egui::Modifiers::default(),
                });
                out.push(egui::Event::PointerGone);
            }
            _ => out.push(egui::Event::PointerMoved(pos)),
        }
    }
    out
}

fn frame(g: &mut Gpu) {
    unsafe {
        if eglMakeCurrent(g.egl.display, g.egl.surface, g.egl.surface, g.egl.context) == 0 {
            return;
        }
    }
    let events = drain_touch_events(g.ppp);
    let raw_input = egui::RawInput {
        screen_rect: Some(egui::Rect::from_min_size(
            egui::pos2(0.0, 0.0),
            egui::vec2(g.w as f32 / g.ppp, g.h as f32 / g.ppp),
        )),
        time: Some(g.start.elapsed().as_secs_f64()),
        events,
        ..Default::default()
    };

    let hook_calls = HOOK_CALLS.load(Ordering::Relaxed);
    let menu = &mut g.menu;
    let full = g.egui_ctx.run(raw_input, |ctx| {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("\u{1F3AE} nextgenlauncher \u{2014} MOD MENU");
            ui.label(
                egui::RichText::new("injected into com.ig.weapon.backpack")
                    .small()
                    .weak(),
            );
            ui.label(format!("il2cpp_string_new hooked calls: {hook_calls}"));
            ui.separator();
            ui.checkbox(&mut menu.god_mode, "God mode");
            ui.checkbox(&mut menu.infinite_gold, "Infinite gold");
            ui.add(egui::Slider::new(&mut menu.speed, 0.5..=5.0).text("Game speed"));
            ui.separator();
            ui.label(
                egui::RichText::new("Rust + egui_glow overlay \u{00B7} no root").small(),
            );
        });
    });

    let ppp = g.ppp;
    let prims = g.egui_ctx.tessellate(full.shapes, ppp);
    unsafe {
        g.gl.disable(glow::SCISSOR_TEST);
        g.gl.viewport(0, 0, g.w, g.h);
        g.gl.clear_color(0.0, 0.0, 0.0, 0.0);
        g.gl.clear(glow::COLOR_BUFFER_BIT);
    }
    g.painter
        .paint_and_update_textures([g.w as u32, g.h as u32], ppp, &prims, &full.textures_delta);
    unsafe {
        eglSwapBuffers(g.egl.display, g.egl.surface);
    }
}

fn render_loop(rx: Receiver<RenderMsg>) {
    let mut gpu: Option<Gpu> = None;
    loop {
        // Handle surface lifecycle messages (non-blocking).
        while let Ok(msg) = rx.try_recv() {
            match msg {
                RenderMsg::Surface(anw, w, h) => {
                    if let Some(g) = gpu.as_mut() {
                        // surfaceChanged (resize) — reconfigure, don't recreate.
                        g.w = w.max(1);
                        g.h = h.max(1);
                        log::info!("[overlay] surface resized {w}x{h}");
                    } else {
                        log::info!("[overlay] creating GPU {w}x{h}");
                        match create_gpu(anw, w, h) {
                            Some(g) => {
                                log::info!("[overlay] egui renderer ready ✓");
                                gpu = Some(g);
                            }
                            None => log::error!("[overlay] GPU init FAILED"),
                        }
                    }
                }
                RenderMsg::Destroyed => {
                    gpu = None;
                }
            }
        }
        if let Some(g) = gpu.as_mut() {
            frame(g);
            std::thread::sleep(Duration::from_millis(16));
        } else {
            std::thread::sleep(Duration::from_millis(50));
        }
    }
}

// ============================ JNI entry points ==============================
#[no_mangle]
pub extern "C" fn JNI_OnLoad(_vm: *mut c_void, _reserved: *mut c_void) -> c_int {
    init_log();
    log::info!(
        "==== MODMENU injected (JNI_OnLoad) inside guest pid={} ====",
        std::process::id()
    );
    std::thread::Builder::new()
        .name("modmenu-hook".into())
        .spawn(hook_worker)
        .ok();
    0x0001_0006
}

#[no_mangle]
pub extern "system" fn Java_menu_nextgen_launcher_OverlayHost_nativeSurface(
    env: *mut SysJNIEnv,
    _cls: jclass,
    surface: jobject,
    w: jint,
    h: jint,
) {
    ensure_render_thread();
    let anw = unsafe { ndk_sys::ANativeWindow_fromSurface(env as *mut _, surface as *mut _) };
    if anw.is_null() {
        log::error!("[overlay] ANativeWindow_fromSurface returned null");
        return;
    }
    log::info!("[overlay] nativeSurface {w}x{h} anw={anw:p}");
    if let Some(tx) = render_tx().lock().unwrap().as_ref() {
        let _ = tx.send(RenderMsg::Surface(anw as usize, w, h));
    }
}

#[no_mangle]
pub extern "system" fn Java_menu_nextgen_launcher_OverlayHost_nativeSurfaceDestroyed(
    _env: *mut SysJNIEnv,
    _cls: jclass,
) {
    if let Some(tx) = render_tx().lock().unwrap().as_ref() {
        let _ = tx.send(RenderMsg::Destroyed);
    }
}

#[no_mangle]
pub extern "system" fn Java_menu_nextgen_launcher_OverlayHost_nativeTouch(
    _env: *mut SysJNIEnv,
    _cls: jclass,
    action: jint,
    x: jfloat,
    y: jfloat,
) {
    touches().lock().unwrap().push((action, x, y));
}
