//! App shell: the eframe::App, the onboarding gate + tab state, and the glue between the
//! UI (Action) and the background workers (Msg).

use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use crate::data::models::SupportedApp;
use crate::data::{build_data_source, AppConfig, Backend, DataSource};
use crate::engine::{build_engine, Engine};
use crate::task::{Msg, Tasks};
use crate::ui::{self, Action, Tab};
use crate::{i18n, platform, theme};

pub const LOGO_W: usize = 360;
pub const LOGO_H: usize = 78;
const LOGO_BYTES: &[u8] = include_bytes!("../assets/nextgen_logo.rgba");

/// Recolor the near-black wordmark pixels to light for the dark theme, preserving the blue
/// "GEN" (only low-saturation dark pixels are lifted, so colored pixels stay untouched).
fn dark_logo_image() -> egui::ColorImage {
    let mut px = LOGO_BYTES.to_vec();
    for c in px.chunks_exact_mut(4) {
        if c[3] > 10 {
            let mx = c[0].max(c[1]).max(c[2]);
            let mn = c[0].min(c[1]).min(c[2]);
            let lum = (c[0] as u32 * 30 + c[1] as u32 * 59 + c[2] as u32 * 11) / 100;
            if (mx - mn) < 40 && lum < 150 {
                c[0] = 0xE7;
                c[1] = 0xEA;
                c[2] = 0xEE;
            }
        }
    }
    egui::ColorImage::from_rgba_unmultiplied([LOGO_W, LOGO_H], &px)
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Lang {
    En,
    Vi,
}
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum ThemeChoice {
    Light,
    Dark,
}
/// In-progress launch flow. The modal shows a single "Launching…" spinner, but internally the
/// flow still runs three phases: step 0 is a brief visual beat; step 1 pre-warms the engine
/// server process (forces it fully up + answering) so the guest never races a half-started
/// server — the black screen we saw after idle; only once it's ready (or a hard cap) does step
/// 2 fire the real launch, after which the modal closes.
struct LaunchFlow {
    pkg: String,
    title: String,
    icon_url: String,
    step: usize,
    step_start: f64,
    /// Set when the pre-warm (server-up round-trip) completes; gates step 1 → 2.
    warmed: bool,
}
/// Visual dwell for the Downloading + Launching steps.
const LAUNCH_STEP_SECS: f64 = 0.7;
/// Hard cap on the Activating pre-warm — proceed to launch even if the server is slow to
/// answer (a cold server cold-start is ~5s; this is the give-up ceiling).
const LAUNCH_WARM_MAX: f64 = 15.0;

pub struct App {
    data: Arc<dyn DataSource>,
    engine: Arc<dyn Engine>,
    tasks: Tasks,
    backend: Backend,

    pub(crate) tab: Tab,
    /// When set (under the Apps tab), the App Details screen for this package is shown.
    pub(crate) detail: Option<String>,
    pub(crate) perm_granted: bool,
    pub(crate) catalog: Vec<SupportedApp>,
    pub(crate) installed: HashSet<String>,
    /// Packages installed on the DEVICE (can be cloned). Empty until `device_known`.
    pub(crate) device_pkgs: HashSet<String>,
    pub(crate) device_known: bool,
    pub(crate) lang: Lang,
    pub(crate) theme_choice: ThemeChoice,
    /// Linked-account state for the Settings sign-in section.
    pub(crate) google_account: Option<String>,
    pub(crate) facebook_linked: bool,
    /// False until the first accounts fetch resolves — drives the one-time loading state
    /// (later refreshes update silently so the row never flickers loading↔loaded).
    pub(crate) accounts_known: bool,
    pub(crate) adding: HashSet<String>,
    pub(crate) loading_catalog: bool,
    pub(crate) logo: Option<egui::TextureHandle>,
    pub(crate) logo_dark: Option<egui::TextureHandle>,
    pub(crate) toast: Option<String>,
    pub(crate) toast_until: f64,
    /// A clone/uninstall is in flight — drives the full-screen loading overlay.
    pub(crate) busy: bool,

    launch_flow: Option<LaunchFlow>,
    loaded: bool,
    last_perm_check: f64,
    theme_applied: ThemeChoice,
    safe_insets: theme::Insets,
    last_insets_check: f64,
    insets_ready: bool,
    insets_tries: u32,
    was_focused: bool,
}

/// Give up probing for real safe-area insets after this many misses (keeps the fallback,
/// and avoids repainting forever on a context that never yields a window).
const INSETS_MAX_TRIES: u32 = 15;

impl App {
    pub fn new(ctx: &egui::Context, _data_dir: Option<PathBuf>) -> Self {
        theme::install(ctx);
        egui_extras::install_image_loaders(ctx); // remote app-icon loading via URL
        let cfg = AppConfig::default();
        let data = build_data_source(&cfg);
        let engine = build_engine();
        let tasks = Tasks::new(data.clone(), engine.clone(), ctx.clone());

        let img = egui::ColorImage::from_rgba_unmultiplied([LOGO_W, LOGO_H], LOGO_BYTES);
        let logo = Some(ctx.load_texture("ng-logo", img, egui::TextureOptions::LINEAR));
        let logo_dark = Some(ctx.load_texture(
            "ng-logo-dark",
            dark_logo_image(),
            egui::TextureOptions::LINEAR,
        ));

        Self {
            data,
            engine,
            tasks,
            backend: cfg.backend,
            tab: Tab::Apps,
            detail: None,
            perm_granted: platform::all_files_access_granted(),
            catalog: Vec::new(),
            installed: HashSet::new(),
            device_pkgs: HashSet::new(),
            device_known: false,
            lang: Lang::En,
            theme_choice: ThemeChoice::Light,
            google_account: None,
            facebook_linked: false,
            accounts_known: false,
            adding: HashSet::new(),
            loading_catalog: false,
            logo,
            logo_dark,
            toast: None,
            toast_until: 0.0,
            busy: false,
            launch_flow: None,
            loaded: false,
            last_perm_check: 0.0,
            theme_applied: ThemeChoice::Light,
            safe_insets: theme::Insets {
                top: theme::SAFE_TOP,
                ..Default::default()
            },
            last_insets_check: 0.0,
            insets_ready: false,
            insets_tries: 0,
            was_focused: false,
        }
    }

    pub(crate) fn backend_name(&self) -> &'static str {
        match self.backend {
            Backend::LocalFake => "local fake",
            Backend::Remote => "remote",
        }
    }

    pub(crate) fn set_toast(&mut self, ctx: &egui::Context, s: impl Into<String>) {
        self.toast = Some(s.into());
        self.toast_until = ctx.input(|i| i.time) + 2.5;
    }

    fn reload(&mut self) {
        self.loading_catalog = true;
        self.tasks.spawn(|cx| {
            let r = cx.data.catalog().map_err(|e| e.to_string());
            cx.send(Msg::CatalogLoaded(r));
        });
        self.spawn_installed_refresh();
        self.spawn_installable_refresh();
        self.spawn_accounts_refresh();
    }

    fn spawn_installed_refresh(&self) {
        self.tasks.spawn(|cx| {
            let r = cx.engine.installed_pkgs(0).map_err(|e| e.to_string());
            cx.send(Msg::InstalledLoaded(r));
        });
    }

    fn spawn_installable_refresh(&self) {
        self.tasks.spawn(|cx| {
            let r = cx.engine.installable_pkgs().map_err(|e| e.to_string());
            cx.send(Msg::InstallableLoaded(r));
        });
    }

    fn spawn_accounts_refresh(&self) {
        self.tasks.spawn(|cx| {
            let r = cx.engine.accounts().map_err(|e| e.to_string());
            cx.send(Msg::AccountsLoaded(r));
        });
    }

    fn on_msg(&mut self, m: Msg, ctx: &egui::Context) {
        match m {
            Msg::CatalogLoaded(r) => {
                self.loading_catalog = false;
                match r {
                    Ok(v) => self.catalog = v,
                    Err(e) => self.set_toast(ctx, format!("Catalog failed: {e}")),
                }
            }
            Msg::InstalledLoaded(r) => {
                self.busy = false; // a clone/uninstall op has settled
                match r {
                    Ok(v) => self.installed = v.into_iter().collect(),
                    Err(e) => self.set_toast(ctx, format!("Installed list failed: {e}")),
                }
            }
            Msg::InstallableLoaded(r) => {
                if let Ok(v) = r {
                    self.device_pkgs = v.into_iter().collect();
                }
                self.device_known = true;
            }
            Msg::AddResult { pkg, res } => {
                self.adding.remove(&pkg);
                self.busy = false;
                match res {
                    Ok(()) => {
                        self.set_toast(ctx, format!("Added {pkg}"));
                        self.spawn_installed_refresh();
                    }
                    Err(e) => self.set_toast(ctx, format!("Add failed: {e}")),
                }
            }
            Msg::LaunchResult { pkg, res } => match res {
                Ok(()) => self.set_toast(ctx, format!("Launched {pkg}")),
                Err(e) => self.set_toast(ctx, format!("Launch failed: {e}")),
            },
            Msg::AccountsLoaded(r) => {
                if let Ok(a) = r {
                    self.google_account = a.google;
                    self.facebook_linked = a.facebook;
                }
                // First result in (Ok or Err) ends the loading state; errors keep the last
                // known values. Best-effort status display.
                self.accounts_known = true;
            }
            Msg::CoreReady => {
                // Pre-warm finished → let the launch modal advance from Activating to Launching.
                if let Some(f) = self.launch_flow.as_mut() {
                    f.warmed = true;
                }
            }
            Msg::Toast(s) => self.set_toast(ctx, s),
        }
    }

    fn on_action(&mut self, a: Action, ctx: &egui::Context) {
        match a {
            Action::None => {}
            Action::SwitchTab(t) => self.tab = t,
            Action::GrantPermission => {
                platform::request_all_files_access();
                // Fake mode grants synchronously; on-device the throttled re-check
                // (see update) flips this once the user returns from Settings.
                self.perm_granted = platform::all_files_access_granted();
            }
            Action::Launch(p) => {
                // Open the launch modal; the real launch fires when the Launching step is
                // reached (see advance_launch_flow).
                let now = ctx.input(|i| i.time);
                let (title, icon_url) = self
                    .catalog
                    .iter()
                    .find(|a| a.pkg == p)
                    .map(|a| (a.title.clone(), a.icon_url.clone()))
                    .unwrap_or_else(|| (p.clone(), String::new()));
                self.launch_flow = Some(LaunchFlow {
                    pkg: p,
                    title,
                    icon_url,
                    step: 0,
                    step_start: now,
                    warmed: false,
                });
            }
            Action::Add(p) => {
                self.adding.insert(p.clone());
                self.busy = true;
                self.tasks.spawn(move |cx| {
                    let r = cx
                        .engine
                        .install_from_device(&p, 0)
                        .map(|_| ())
                        .map_err(|e| e.to_string());
                    cx.send(Msg::AddResult { pkg: p, res: r });
                });
            }
            Action::Uninstall(p) => {
                self.busy = true;
                self.tasks.spawn(move |cx| match cx.engine.uninstall(&p, 0) {
                    Ok(()) => {
                        cx.send(Msg::Toast(format!("Removed {p}")));
                        let inst = cx.engine.installed_pkgs(0).map_err(|e| e.to_string());
                        cx.send(Msg::InstalledLoaded(inst));
                    }
                    Err(e) => cx.send(Msg::Toast(format!("Uninstall failed: {e}"))),
                });
            }
            Action::OpenDetail(p) => self.detail = Some(p),
            Action::Back => self.detail = None,
            Action::OpenUrl(u) => {
                self.tasks.spawn(move |_cx| platform::open_url(&u));
            }
            Action::Toast(s) => self.set_toast(ctx, s),
            Action::SetLang(l) => self.lang = l,
            Action::SetTheme(t) => self.theme_choice = t,
            Action::SignInGoogle => {
                self.set_toast(ctx, i18n::tr(self.lang, "toast.signin"));
                // Launch only: the WebView login is async. The account row refreshes when the
                // launcher regains focus after the login activity closes (see update()).
                // Reading accounts() here would capture pre-login (stale) state.
                self.tasks.spawn(|cx| {
                    let _ = cx.engine.start_google_sign_in();
                });
            }
            Action::SignInFacebook => {
                self.set_toast(ctx, i18n::tr(self.lang, "toast.signin"));
                // Launch only: the web login is async; the row refreshes on focus-regain.
                self.tasks.spawn(|cx| {
                    let _ = cx.engine.start_facebook_sign_in();
                });
            }
            Action::SignOutGoogle => {
                self.set_toast(ctx, i18n::tr(self.lang, "toast.signout"));
                self.tasks.spawn(|cx| {
                    let _ = cx.engine.google_sign_out();
                    let r = cx.engine.accounts().map_err(|e| e.to_string());
                    cx.send(Msg::AccountsLoaded(r));
                });
            }
            Action::SignOutFacebook => {
                self.set_toast(ctx, i18n::tr(self.lang, "toast.signout"));
                self.tasks.spawn(|cx| {
                    let _ = cx.engine.facebook_sign_out();
                    let r = cx.engine.accounts().map_err(|e| e.to_string());
                    cx.send(Msg::AccountsLoaded(r));
                });
            }
        }
    }

    /// Pull the real safe-area insets (throttled) and publish them for this frame. Runs
    /// event-driven (a rotation's resize event re-reads them); no background polling.
    fn sync_insets(&mut self, ctx: &egui::Context, now: f64) {
        if now - self.last_insets_check > 0.4 {
            self.last_insets_check = now;
            match platform::safe_insets_px() {
                Some(px) => {
                    let ppp = ctx.pixels_per_point().max(0.1);
                    self.safe_insets = theme::Insets {
                        top: px[0] / ppp,
                        left: px[1] / ppp,
                        right: px[2] / ppp,
                        bottom: px[3] / ppp,
                    };
                    self.insets_ready = true;
                }
                None => self.insets_tries += 1, // keep the fallback insets
            }
        }
        theme::set_insets(self.safe_insets);
    }

    /// Drive the launch modal. Step 1 ("Activating") holds until the engine server is up +
    /// answering (a pre-warm round-trip), so the guest launched in step 2 doesn't race a
    /// half-started server (the black screen after idle). The modal closes once "Launching"
    /// has had its visual beat.
    fn advance_launch_flow(&mut self, ctx: &egui::Context, now: f64) {
        let Some(mut f) = self.launch_flow.take() else {
            return;
        };
        let mut keep = true;
        let elapsed = now - f.step_start;
        match f.step {
            // Downloading — a brief visual beat, then start pre-warming the server.
            0 => {
                if elapsed > LAUNCH_STEP_SECS {
                    f.step = 1;
                    f.step_start = now;
                    // Force the engine server process fully up + answering BEFORE we launch
                    // the guest. This binder round-trip blocks (~5s if the server was killed
                    // by the freezer while idle); the Activating spinner covers it.
                    self.tasks.spawn(|cx| {
                        let _ = cx.engine.installed_pkgs(0);
                        cx.send(Msg::CoreReady);
                    });
                }
            }
            // Activating — wait for the server to be ready (or the hard cap), then launch.
            1 => {
                if f.warmed || elapsed > LAUNCH_WARM_MAX {
                    f.step = 2;
                    f.step_start = now;
                    let p = f.pkg.clone();
                    self.tasks.spawn(move |cx| {
                        let r = cx.engine.launch(&p, 0).map(|_| ()).map_err(|e| e.to_string());
                        cx.send(Msg::LaunchResult { pkg: p, res: r });
                    });
                }
            }
            // Launching — visual beat, then close (launch already dispatched).
            _ => {
                if elapsed > LAUNCH_STEP_SECS {
                    keep = false;
                }
            }
        }
        if keep {
            self.launch_flow = Some(f);
            ctx.request_repaint_after(Duration::from_millis(80));
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        for m in self.tasks.drain() {
            self.on_msg(m, ctx);
        }
        if !self.loaded {
            self.loaded = true;
            self.reload();
        }

        // Apply a theme change (light↔dark) the frame after the user picks it.
        if self.theme_applied != self.theme_choice {
            theme::apply(ctx, self.theme_choice);
            self.theme_applied = self.theme_choice;
        }

        let now = ctx.input(|i| i.time);
        self.sync_insets(ctx, now);
        // Repaint briefly at startup only, until the real insets settle (or we give up).
        if !self.insets_ready && self.insets_tries < INSETS_MAX_TRIES {
            ctx.request_repaint_after(Duration::from_millis(300));
        }
        self.advance_launch_flow(ctx, now);

        if !self.perm_granted {
            // Onboarding actively polls the permission (the user is expected to grant it).
            ctx.request_repaint_after(Duration::from_millis(400));
            if now - self.last_perm_check > 0.4 {
                self.last_perm_check = now;
                self.perm_granted = platform::all_files_access_granted();
            }
            let action = ui::onboarding::render(self, ctx);
            self.on_action(action, ctx);
        } else {
            // No background polling. Refresh volatile state (permission + linked accounts)
            // only when the app regains focus — e.g. returning from the login/permission
            // page — so a fresh status shows without a periodic poll (which flickered).
            let focused = ctx.input(|i| i.focused);
            if focused && !self.was_focused {
                self.perm_granted = platform::all_files_access_granted();
                self.spawn_accounts_refresh();
                // Device app set may have changed (e.g. user installed the game from Play).
                self.spawn_installable_refresh();
            }
            self.was_focused = focused;

            // Bottom nav first: a tab tap also exits the details sub-screen (back to root).
            if let Some(t) = ui::bottom_nav(ctx, self.tab, self.lang) {
                self.detail = None;
                self.tab = t;
            }
            let showing_detail = self.tab == Tab::Apps && self.detail.is_some();
            let title = if showing_detail {
                i18n::tr(self.lang, "details.title")
            } else {
                self.tab.title(self.lang)
            };
            let logo_h = if self.theme_choice == ThemeChoice::Dark {
                self.logo_dark.as_ref()
            } else {
                self.logo.as_ref()
            };
            // Back button lives in the top nav (shown only on the details sub-screen).
            if ui::top_nav(ctx, title, logo_h.map(|h| h.id()), showing_detail) {
                self.detail = None;
            }
            let action = if self.tab == Tab::Apps && self.detail.is_some() {
                ui::details::render(self, ctx)
            } else {
                match self.tab {
                    Tab::Apps => ui::apps::render(self, ctx),
                    Tab::Settings => ui::settings::render(self, ctx),
                }
            };
            // A user action (theme/lang/…) takes effect next frame — schedule that frame.
            let changed = !matches!(action, Action::None);
            self.on_action(action, ctx);
            if changed {
                ctx.request_repaint();
            }
        }

        // Launch modal (single "Launching…" spinner) over everything.
        if let Some(f) = &self.launch_flow {
            ui::launch_modal(ctx, &f.icon_url, &f.title, self.lang);
        }
        // Full-screen loading overlay while a clone/uninstall runs (blocks input).
        if self.busy {
            ui::loading_overlay(ctx);
        }
        ui::toast(self, ctx);
    }
}
