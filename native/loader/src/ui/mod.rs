//! egui UI: the widget kit + the screens, built to the designs. Runtime light/dark
//! palette (see `theme::pal`), Be Vietnam Pro type, key-based i18n (`i18n::tr`). Screens are
//! stateless views over `&mut App` that return an `Action`.

pub mod apps;
pub mod details;
pub mod onboarding;
pub mod settings;

use egui::{
    pos2, vec2, Align2, Color32, CornerRadius, FontFamily, FontId, Margin, Pos2, Rect, RichText,
    Sense, Stroke,
};

use crate::app::{App, Lang, ThemeChoice};
use crate::data::models::Tier;
use crate::i18n::tr;
use crate::theme::*;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Apps,
    Settings,
}

impl Tab {
    /// The localized screen name shown at the left of the top bar / in the bottom nav.
    pub fn title(self, lang: Lang) -> &'static str {
        match self {
            Tab::Apps => tr(lang, "nav.apps"),
            Tab::Settings => tr(lang, "nav.settings"),
        }
    }
}

pub enum Action {
    None,
    GrantPermission,
    SwitchTab(Tab),
    Launch(String),
    Add(String),
    Uninstall(String),
    OpenDetail(String),
    Back,
    OpenUrl(String),
    Toast(String),
    SetLang(Lang),
    SetTheme(ThemeChoice),
    SignInGoogle,
    SignInFacebook,
    SignOutGoogle,
    SignOutFacebook,
}

// ---------------------------------------------------------------- primitives ----------

pub fn logo(ui: &mut egui::Ui, tex: egui::TextureId, height: f32) {
    let w = height * (crate::app::LOGO_W as f32 / crate::app::LOGO_H as f32);
    ui.add(egui::Image::new(egui::load::SizedTexture::new(tex, vec2(w, height))));
}

/// Dark rounded tile with a 2-letter white monogram.
pub fn monogram(ui: &mut egui::Ui, letters: &str, size: f32) {
    let c = pal();
    let (rect, _) = ui.allocate_exact_size(vec2(size, size), Sense::hover());
    let p = ui.painter_at(rect);
    p.rect_filled(rect, CornerRadius::same(3), c.tile);
    p.text(
        rect.center(),
        Align2::CENTER_CENTER,
        letters,
        FontId::proportional(size * 0.36),
        c.on_accent,
    );
}

/// Small pill badge (shared shape for tier + install status).
fn pill(ui: &mut egui::Ui, text: &str, fill: Color32, fg: Color32, stroke: Stroke) {
    egui::Frame::group(ui.style())
        .fill(fill)
        .stroke(stroke)
        .inner_margin(Margin::symmetric(6, 1))
        .corner_radius(CornerRadius::same(2))
        .show(ui, |ui| {
            ui.label(RichText::new(text).size(9.0).color(fg).extra_letter_spacing(1.0));
        });
}

pub fn tier_badge(ui: &mut egui::Ui, tier: Tier) {
    let c = pal();
    match tier {
        Tier::Vip => pill(ui, tier.label(), c.tile, c.on_accent, Stroke::NONE),
        Tier::Plus => pill(ui, tier.label(), c.bg, c.muted, Stroke::new(1.0, c.border)),
    }
}

/// "Installed" badge shown next to the tier badge (only for cloned apps).
pub fn install_badge(ui: &mut egui::Ui, lang: Lang) {
    let c = pal();
    pill(ui, tr(lang, "badge.installed"), c.bg, c.ok, Stroke::new(1.0, c.ok));
}

/// Installed / Not-installed status pill (App-List badge styling), for the details status row.
pub fn status_badge(ui: &mut egui::Ui, installed: bool, lang: Lang) {
    let c = pal();
    if installed {
        pill(ui, tr(lang, "badge.installed"), c.bg, c.ok, Stroke::new(1.0, c.ok));
    } else {
        pill(ui, tr(lang, "badge.not_installed"), c.bg, c.faint, Stroke::new(1.0, c.border));
    }
}

/// Full-width filled accent button. Returns true on click.
pub fn primary_button(ui: &mut egui::Ui, label: &str) -> bool {
    let c = pal();
    let text = bold(label.to_uppercase(), 13.0)
        .color(c.on_accent)
        .extra_letter_spacing(1.5);
    ui.add_sized(
        [ui.available_width(), 46.0],
        egui::Button::new(text)
            .fill(c.accent)
            .corner_radius(CornerRadius::same(4)),
    )
    .clicked()
}

/// Full-width outlined accent button.
pub fn outline_button(ui: &mut egui::Ui, label: &str) -> bool {
    let c = pal();
    let text = bold(label.to_uppercase(), 13.0)
        .color(c.accent)
        .extra_letter_spacing(1.5);
    ui.add_sized(
        [ui.available_width(), 44.0],
        egui::Button::new(text)
            .fill(Color32::TRANSPARENT)
            .stroke(Stroke::new(1.0, c.accent))
            .corner_radius(CornerRadius::same(4)),
    )
    .clicked()
}

/// The grey "01  SECTION ................ RIGHT" band.
pub fn section_band(ui: &mut egui::Ui, num: &str, title: &str, right: Option<(&str, Color32)>) {
    let c = pal();
    egui::Frame::group(ui.style())
        .fill(c.band)
        .stroke(Stroke::NONE)
        .corner_radius(CornerRadius::ZERO)
        .inner_margin(Margin::symmetric(12, 6))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.horizontal(|ui| {
                ui.label(bold(num, 10.0).color(c.accent).extra_letter_spacing(1.0));
                ui.add_space(6.0);
                ui.label(
                    RichText::new(title.to_uppercase())
                        .size(10.0)
                        .color(c.muted)
                        .extra_letter_spacing(2.0),
                );
                if let Some((r, col)) = right {
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(
                            RichText::new(r.to_uppercase())
                                .size(10.0)
                                .color(col)
                                .extra_letter_spacing(1.0),
                        );
                    });
                }
            });
        });
}

pub struct CardView<'a> {
    pub monogram: String,
    pub pkg: &'a str,
    pub title: &'a str,
    pub installed: bool,
    pub adding: bool,
    pub lang: Lang,
    /// Remote icon URL (Play-Store CDN). Empty → fall back to the monogram tile.
    pub icon_url: &'a str,
    /// The app object this card renders — passed to render-hook plugins (e.g. Tier,
    /// which reads `app.tier`). Tier is no longer a core CardView field.
    pub app: &'a crate::data::models::SupportedApp,
}

/// One app card in the 2-column grid. Returns true when tapped.
pub fn app_card(ui: &mut egui::Ui, cv: &CardView) -> bool {
    let c = pal();
    let resp = egui::Frame::group(ui.style())
        .fill(c.bg)
        .stroke(Stroke::NONE)
        .inner_margin(Margin::symmetric(4, 6))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.horizontal(|ui| {
                if cv.icon_url.is_empty() {
                    monogram(ui, &cv.monogram, 44.0);
                } else {
                    // Remote icon: egui_extras' image loader fetches + caches it async,
                    // showing a spinner until it arrives.
                    ui.add(
                        egui::Image::new(cv.icon_url)
                            .fit_to_exact_size(egui::vec2(44.0, 44.0))
                            .maintain_aspect_ratio(true)
                            .corner_radius(CornerRadius::same(3)),
                    );
                }
                ui.add_space(6.0);
                ui.vertical(|ui| {
                    // Ellipsis-truncate the package id to a single line (no wrap).
                    ui.add(
                        egui::Label::new(RichText::new(cv.pkg).size(10.0).color(c.muted))
                            .truncate(),
                    );
                    ui.add_space(3.0);
                    ui.horizontal_wrapped(|ui| {
                        ui.spacing_mut().item_spacing = vec2(6.0, 4.0);
                        // Tier badge is rendered by a plugin (plugins/tier/tier.rs) —
                        // the core/free card shows none unless a plugin registers this slot.
                        crate::plugin::fire_render(
                            crate::plugin::Slot::AppCardBadge,
                            ui,
                            &crate::plugin::RenderCtx { app: cv.app, lang: cv.lang },
                        );
                        // Only the "Installed" badge is shown; supported apps get none.
                        if cv.installed {
                            install_badge(ui, cv.lang);
                        }
                    });
                });
            });
            ui.add_space(6.0);
            // Single-line, ellipsis-truncated, not uppercased.
            ui.add(egui::Label::new(bold(cv.title, 12.5).color(c.text)).truncate());
            if cv.adding {
                ui.add_space(2.0);
                ui.label(
                    RichText::new(tr(cv.lang, "card.adding"))
                        .size(9.0)
                        .color(c.accent)
                        .extra_letter_spacing(1.5),
                );
            }
        })
        .response;
    resp.interact(Sense::click()).clicked()
}

// ---------------------------------------------------------------- top nav -------------

/// Shared top bar: screen name on the left, logo on the right, with top padding to clear
/// the camera cutout. Every main screen renders this once per frame.
/// Shared top bar. When `back` is true, a back chevron is drawn left of the title; the
/// return value is whether it was tapped.
pub fn top_nav(
    ctx: &egui::Context,
    title: &str,
    logo_id: Option<egui::TextureId>,
    back: bool,
) -> bool {
    let c = pal();
    let i = insets();
    let mut back_clicked = false;
    egui::TopBottomPanel::top("ng-top")
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(c.bg)
                .stroke(Stroke::NONE)
                .inner_margin(margin(16.0 + i.left, 16.0 + i.right, 10.0 + i.top, 10.0)),
        )
        .show(ctx, |ui| {
            ui.horizontal(|ui| {
                if back {
                    let (r, resp) = ui.allocate_exact_size(vec2(24.0, 24.0), Sense::click());
                    let p = ui.painter_at(r);
                    let cx = r.center().x;
                    let cy = r.center().y;
                    let st = Stroke::new(2.0, c.text);
                    p.line_segment([pos2(cx + 4.0, cy - 6.0), pos2(cx - 4.0, cy)], st);
                    p.line_segment([pos2(cx - 4.0, cy), pos2(cx + 4.0, cy + 6.0)], st);
                    back_clicked = resp.clicked();
                    ui.add_space(10.0);
                }
                ui.label(bold(title, 19.0).color(c.text).extra_letter_spacing(1.5));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if let Some(t) = logo_id {
                        logo(ui, t, 20.0);
                    }
                });
            });
        });
    back_clicked
}

// ---------------------------------------------------------------- bottom nav ----------

pub fn bottom_nav(ctx: &egui::Context, active: Tab, lang: Lang) -> Option<Tab> {
    let c = pal();
    let i = insets();
    let mut clicked = None;
    egui::TopBottomPanel::bottom("ng-nav")
        .exact_height(62.0 + i.bottom)
        .frame(egui::Frame::group(&ctx.style()).fill(c.nav_bg).stroke(Stroke::NONE).corner_radius(CornerRadius::ZERO).inner_margin(margin(i.left, i.right, 0.0, i.bottom)))
        .show(ctx, |ui| {
            let full = ui.available_width();
            let h = ui.available_height();
            ui.spacing_mut().item_spacing.x = 0.0; // exact equal shares, no overflow
            ui.horizontal(|ui| {
                let tabs = [Tab::Apps, Tab::Settings];
                let tab_w = full / tabs.len() as f32;
                for tab in tabs {
                    let (rect, resp) = ui.allocate_exact_size(vec2(tab_w, h), Sense::click());
                    let on = tab == active;
                    let col = if on { c.accent } else { c.nav_inactive };
                    let p = ui.painter_at(rect);
                    if on {
                        p.rect_filled(
                            Rect::from_min_size(rect.left_top(), vec2(rect.width(), 2.0)),
                            CornerRadius::ZERO,
                            c.accent,
                        );
                    }
                    draw_icon(&p, tab, rect.center() - vec2(0.0, 8.0), 20.0, col);
                    p.text(
                        rect.center() + vec2(0.0, 16.0),
                        Align2::CENTER_CENTER,
                        tab.title(lang),
                        FontId::proportional(9.5),
                        col,
                    );
                    if resp.clicked() {
                        clicked = Some(tab);
                    }
                }
            });
        });
    clicked
}

fn draw_icon(p: &egui::Painter, tab: Tab, c: Pos2, s: f32, col: Color32) {
    let st = Stroke::new(1.6, col);
    match tab {
        Tab::Apps => {
            let d = s * 0.30;
            let g = s * 0.26;
            for (dx, dy) in [(-1.0, -1.0), (1.0, -1.0), (-1.0, 1.0), (1.0, 1.0)] {
                let cc = c + vec2(dx * g, dy * g);
                p.rect_filled(Rect::from_center_size(cc, vec2(d, d)), CornerRadius::same(1), col);
            }
        }
        Tab::Settings => {
            p.circle_stroke(c, s * 0.16, st);
            for k in 0..8 {
                let a = k as f32 / 8.0 * std::f32::consts::TAU;
                let d = vec2(a.cos(), a.sin());
                p.line_segment([c + d * s * 0.26, c + d * s * 0.40], st);
            }
        }
    }
}

// ---------------------------------------------------------------- loading overlay -----

/// Full-screen modal loading overlay (dims the screen + centered spinner, swallows input).
/// Shown while a clone/uninstall is in flight.
pub fn loading_overlay(ctx: &egui::Context) {
    let screen = ctx.screen_rect();
    egui::Area::new(egui::Id::new("ng-loading"))
        .order(egui::Order::Foreground)
        .fixed_pos(screen.min)
        .interactable(true)
        .show(ctx, |ui| {
            // Swallow all pointer input over the whole screen.
            let _ = ui.allocate_rect(screen, Sense::click_and_drag());
            ui.painter()
                .rect_filled(screen, CornerRadius::ZERO, Color32::from_black_alpha(140));
            let sp = Rect::from_center_size(screen.center(), vec2(48.0, 48.0));
            ui.put(sp, egui::Spinner::new().size(42.0).color(Color32::WHITE));
        });
    ctx.request_repaint();
}

// ---------------------------------------------------------------- launch modal --------

/// Central launch modal: app icon + name and a single indeterminate "Launching…" spinner,
/// shown while the engine warms up and the guest is dispatched (see advance_launch_flow).
pub fn launch_modal(ctx: &egui::Context, icon_url: &str, title: &str, lang: Lang) {
    let c = pal();
    let screen = ctx.screen_rect();
    egui::Area::new(egui::Id::new("ng-launch"))
        .order(egui::Order::Foreground)
        .fixed_pos(screen.min)
        .interactable(true)
        .show(ctx, |ui| {
            let _ = ui.allocate_rect(screen, Sense::click_and_drag());
            let p = ui.painter().clone();
            p.rect_filled(screen, CornerRadius::ZERO, Color32::from_rgba_premultiplied(0, 0, 0, 150));

            let cw = 320.0_f32.min(screen.width() - 48.0);
            let card = Rect::from_center_size(screen.center(), vec2(cw, 196.0));
            p.rect_filled(card, CornerRadius::same(12), c.bg);

            if !icon_url.is_empty() {
                let ir = Rect::from_center_size(pos2(card.center().x, card.top() + 54.0), vec2(60.0, 60.0));
                egui::Image::new(icon_url)
                    .maintain_aspect_ratio(true)
                    .corner_radius(CornerRadius::same(14))
                    .paint_at(ui, ir);
            }
            p.text(
                pos2(card.center().x, card.top() + 104.0),
                Align2::CENTER_CENTER,
                title,
                FontId::new(16.0, FontFamily::Name(BOLD_FAMILY.into())),
                c.text,
            );

            // Single indeterminate spinner + "Launching…" label (no per-step breakdown).
            let spin = Rect::from_center_size(pos2(card.center().x, card.top() + 140.0), vec2(24.0, 24.0));
            ui.put(spin, egui::Spinner::new().size(22.0).color(c.accent));
            p.text(
                pos2(card.center().x, card.top() + 172.0),
                Align2::CENTER_CENTER,
                &format!("{}…", tr(lang, "launch.launching")),
                FontId::proportional(12.0),
                c.muted,
            );
        });
    ctx.request_repaint();
}

// ---------------------------------------------------------------- toast ---------------

pub fn toast(app: &mut App, ctx: &egui::Context) {
    let c = pal();
    let now = ctx.input(|i| i.time);
    if let Some(t) = app.toast.clone() {
        if now < app.toast_until {
            egui::Area::new(egui::Id::new("ng-toast"))
                .anchor(egui::Align2::CENTER_BOTTOM, vec2(0.0, -80.0))
                .show(ctx, |ui| {
                    egui::Frame::group(ui.style())
                        .fill(c.tile)
                        .stroke(Stroke::NONE)
                        .corner_radius(CornerRadius::same(6))
                        .inner_margin(Margin::symmetric(14, 10))
                        .show(ui, |ui| {
                            ui.label(RichText::new(t).color(c.on_accent).size(12.0));
                        });
                });
            ctx.request_repaint_after(std::time::Duration::from_millis(200));
        } else {
            app.toast = None;
        }
    }
}
