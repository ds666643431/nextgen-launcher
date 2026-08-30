//! Unified plugin registry — ONE registration place, and a single plugin can hook
//! BOTH app-lifecycle events AND UI rendering.
//!
//! egui is Rust, so the registry and the plugin objects are Rust. A plugin exposes
//! one `register(&mut Registry)` fn and inside it registers whatever hooks it wants:
//!   * `on_event(Event, ..)` — lifecycle (Start / clone / launch / …); may **veto** pre-events.
//!   * `on_render(Slot, ..)` — draw into the UI at a named slot.
//! Sensitive / obfuscated NATIVE detection stays in C++ and is called via FFI from a
//! plugin's event hook (e.g. `ng_rootbeer_scan` / `ng_reveny_scan`). Every plugin is
//! registered in ONE file: plugins/register.rs. See docs/plugin-architecture-plan.md.
//!
//! Policy (hardening-plan §6): telemetry / soft-gate only; the server withholding
//! keys is the durable barrier. Plugins are trusted in-process code you ship.

use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::OnceLock;

use crate::app::Lang;
use crate::data::models::SupportedApp;

// ── plugins live under plugins/ (co-located, both Rust and C++), pulled in via #[path].
//    register.rs is the ONE activation point; edit it to enable/disable plugins.
#[path = "../../plugins/register.rs"]
mod register;

/// App-lifecycle events. Fired by the launcher (android_main / the task worker).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Event {
    Start,
    AppPreClone,
    AppPostClone,
    AppPreRemove,
    AppPostRemove,
    AppPreLaunch,
    AppLaunch,
    AppPostLaunch,
    Stop,
}

/// Named UI extension points where the core UI invites plugin rendering.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Slot {
    /// Inside an app card, under the app id (e.g. a tier badge).
    AppCardBadge,
    /// In app details, before the description (e.g. a tier row).
    AppDetailsBeforeDescription,
}

/// Result of an event hook. A `Veto` on a pre-event aborts that operation
/// (soft gate — the durable denial is the server refusing keys).
#[derive(Clone, Debug)]
pub enum Outcome {
    Continue,
    Veto(u32),
}

/// What an event hook receives. `package` is `None` for non-app events (Start/Stop).
pub struct EventCtx<'a> {
    pub package: Option<&'a str>,
    pub user_id: i32,
}

/// What a render hook receives: the app object the slot renders for (tier and every
/// other field come from here — the data source) plus the current language.
pub struct RenderCtx<'a> {
    pub app: &'a SupportedApp,
    pub lang: Lang,
}

type EventHook = Box<dyn Fn(&EventCtx) -> Outcome + Send + Sync>;
type RenderHook = Box<dyn Fn(&mut egui::Ui, &RenderCtx) + Send + Sync>;

/// The one registry: event hooks + render hooks. Registration order = dispatch order.
#[derive(Default)]
pub struct Registry {
    event: Vec<(Event, EventHook)>,
    render: Vec<(Slot, RenderHook)>,
}

impl Registry {
    /// Register a lifecycle hook. Return `Outcome::Veto` from a pre-event to block it.
    pub fn on_event<F>(&mut self, event: Event, hook: F)
    where
        F: Fn(&EventCtx) -> Outcome + Send + Sync + 'static,
    {
        self.event.push((event, Box::new(hook)));
    }

    /// Register a UI render hook at a slot.
    pub fn on_render<F>(&mut self, slot: Slot, hook: F)
    where
        F: Fn(&mut egui::Ui, &RenderCtx) + Send + Sync + 'static,
    {
        self.render.push((slot, Box::new(hook)));
    }
}

static REGISTRY: OnceLock<Registry> = OnceLock::new();

/// Last environment bitmask a plugin reported (OR-accumulated). Telemetry only.
static REPORTED_FLAGS: AtomicU32 = AtomicU32::new(0);

/// Build the registry once (runs plugins/register.rs). Call at startup, before the
/// first event fires or the first UI frame.
pub fn init() {
    let _ = REGISTRY.set({
        let mut reg = Registry::default();
        register::register_plugins(&mut reg);
        reg
    });
}

/// Fire a lifecycle event into every hook registered for it (in order). The first
/// `Veto` wins (all hooks still run). `package` is `None` for Start/Stop.
pub fn fire_event(event: Event, package: Option<&str>, user_id: i32) -> Outcome {
    let ctx = EventCtx { package, user_id };
    let mut veto: Option<u32> = None;
    if let Some(reg) = REGISTRY.get() {
        for (e, hook) in &reg.event {
            if *e == event {
                if let Outcome::Veto(c) = hook(&ctx) {
                    veto.get_or_insert(c);
                }
            }
        }
    }
    match veto {
        Some(c) => Outcome::Veto(c),
        None => Outcome::Continue,
    }
}

/// Run every render hook registered at `slot`, in order. No-op if none.
pub fn fire_render(slot: Slot, ui: &mut egui::Ui, ctx: &RenderCtx) {
    if let Some(reg) = REGISTRY.get() {
        for (s, hook) in &reg.render {
            if *s == slot {
                hook(ui, ctx);
            }
        }
    }
}

/// Telemetry sink a plugin calls with a detection bitmask (OR-accumulates).
#[allow(dead_code)]
pub fn report_flags(mask: u32) {
    REPORTED_FLAGS.fetch_or(mask, Ordering::Relaxed);
}

/// The last reported environment bitmask (telemetry). Folds into the auth payload.
#[allow(dead_code)]
pub fn reported_flags() -> u32 {
    REPORTED_FLAGS.load(Ordering::Relaxed)
}

extern "C" {
    // Weak no-op in hardening/src/tls_shim.c; a plugin's C++ TU (e.g. the reveny
    // detector) may provide a strong raw-svc kill ladder that overrides it.
    fn ng_force_close();
}

/// Explicitly-invoked hard force-close via a plugin's raw-`svc` kill ladder (a weak
/// no-op if none is provided). Does NOT return when a real ladder is present.
/// Deliberately NOT wired to detection — call only on a server-/policy decision.
#[allow(dead_code)]
pub fn force_close() {
    // SAFETY: leaf FFI to the provider-supplied kill primitive.
    unsafe { ng_force_close() }
}
