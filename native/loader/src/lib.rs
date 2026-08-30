//! nextgenlauncher loader — native Rust/egui launcher (M2–M6, stop-at-auth).
//!
//! Implements launcher-plan §2–§6: the layered architecture, the data adapter
//! (DataSource + fake/remote switch), the engine bridge (FakeEngine + JNI RealEngine),
//! and the app-management screens (Installed + Supported, details, Add→Installed).
//! Auth (§7), the launch/injection pipeline (§8), and the Java floor (§9) are
//! deliberately NOT included yet.
//!
//! Hardening (§10) is delivered as a PLUGIN system (see docs/plugin-architecture-plan.md):
//!   * `plugin` — the Rust bridge to the native C++ plugin registry. Plugins hook
//!     lifecycle events; the registered root-detector plugin (RootBeerFresh or the
//!     reveny port, chosen in plugins/register.cpp) reports on Start. TELEMETRY only.
//!   * `obs` — Rust-side literal obfuscation (obfstr), always on.
//! There is no `commercial` feature — the framework is always compiled; which
//! plugins run is a manual choice in plugins/register.cpp.
//!
//! Runs entirely on fake data by default (LocalFake + FakeEngine) — on-device or,
//! for fast iteration, `cargo check` on the host. Flip `--features real_engine`
//! (after M2a wires Bcore) for the JNI engine; `--features remote` (M5) for the
//! server backend.

#[macro_use]
mod obs;

mod app;
mod data;
mod engine;
mod i18n;
mod platform;
mod plugin;
mod task;
mod theme;
mod ui;

#[cfg(target_os = "android")]
use winit::platform::android::activity::AndroidApp;

#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(android: AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("nextgen"),
    );
    log::info!("nextgen loader android_main() start");

    // Plugin lifecycle (§10). Register plugins (plugins/register.cpp), then fire
    // Start — which runs the registered root-detector plugin. TELEMETRY only today:
    // the server withholding keys stays the durable gate; never decide/hard-block
    // here. The reported bitmask folds into the auth payload once auth (§7) is wired.
    plugin::init();
    let _ = plugin::fire_event(plugin::Event::Start, None, 0);
    let hw = plugin::reported_flags();
    if hw != 0 {
        log::warn!("plugin: environment flags={hw:#x}");
    }

    let data_dir = android.internal_data_path();
    let options = eframe::NativeOptions {
        android_app: Some(android),
        // Glow (GLES/EGL), NOT wgpu: eframe's wgpu backend intermittently fails to rebuild its
        // surface when the Android window is recreated on resume (logs "No window - clearing all
        // surfaces" then never redraws → launcher stuck on the splash after reopen). Glow's
        // hand-managed EGL surface re-acquires reliably across background/foreground. (The
        // overlay already uses egui_glow for a related reason.) Real devices render fine on GLES.
        renderer: eframe::Renderer::Glow,
        ..Default::default()
    };

    let result = eframe::run_native(
        "NextGEN",
        options,
        Box::new(move |cc| Ok(Box::new(app::App::new(&cc.egui_ctx, data_dir)))),
    );
    let _ = plugin::fire_event(plugin::Event::Stop, None, 0);
    if let Err(err) = result {
        log::error!("eframe exited with error: {err:?}");
    }
    // android_main is a ONCE-PER-PROCESS winit entry: if it ever returns (winit exited the event
    // loop), this process must NOT be reused for a later activity — a second GameActivity.onCreate
    // would deadlock in the native glue (stuck splash). Exit so the next launch is a fresh process
    // with a fresh android_main. (MainActivity.onCreate has the primary guard; this is the belt.)
    // :core is a separate process and is unaffected.
    log::info!("nextgen loader: android_main returned — exiting process for a clean next start");
    std::process::exit(0);
}
