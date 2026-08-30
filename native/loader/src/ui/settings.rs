//! Settings: permissions (All-Files-Access), sign-in options (Google / Facebook), theme
//! scheme and language. When an account is linked the row shows the account plus SWITCH /
//! LOGOUT actions. Theme and language use the same round radio-dot style. The screen name
//! lives in the shared top bar, so this view renders only its `CentralPanel`.

use egui::text::LayoutJob;
use egui::{
    vec2, Align2, Button, CornerRadius, FontFamily, FontId, Margin, Pos2, RichText, Sense, Stroke,
    TextFormat,
};

use super::{section_band, Action};
use crate::app::{App, Lang, ThemeChoice};
use crate::i18n::tr;
use crate::theme::*;

/// What the user tapped on an account row.
enum RowAct {
    None,
    SignIn,
    Switch,
    Logout,
}

/// A compact pill button (SWITCH / LOGOUT). `primary` → filled accent, else outlined.
fn mini_btn(ui: &mut egui::Ui, label: &str, primary: bool) -> bool {
    let c = pal();
    let (fill, fg, stroke) = if primary {
        (c.accent, c.on_accent, Stroke::NONE)
    } else {
        (c.bg, c.accent, Stroke::new(1.0, c.accent))
    };
    ui.add(
        Button::new(RichText::new(label).size(10.0).color(fg).extra_letter_spacing(0.5))
            .fill(fill)
            .stroke(stroke)
            .corner_radius(CornerRadius::same(3)),
    )
    .clicked()
}

fn letter_tile(ui: &mut egui::Ui, letter: &str) {
    let c = pal();
    let (r, _) = ui.allocate_exact_size(vec2(22.0, 22.0), Sense::hover());
    let p = ui.painter_at(r);
    p.rect_filled(r, CornerRadius::same(3), c.band);
    p.text(r.center(), Align2::CENTER_CENTER, letter, FontId::proportional(12.0), c.accent);
}

/// One sign-in row (contents vertically centered). `loading` → brand + spinner. Signed out →
/// brand + a LOGIN button (only the button starts sign-in). Signed in → brand + account
/// detail on the left, SWITCH + LOGOUT on the right.
fn account_row(
    ui: &mut egui::Ui,
    letter: &str,
    brand: &str,
    detail: Option<&str>,
    signed: bool,
    loading: bool,
    lang: Lang,
) -> RowAct {
    let c = pal();
    let mut act = RowAct::None;
    // A signed-in row is taller (brand + email); loading / not-signed rows are single-line.
    let two_line = signed && !loading;
    let row_h = if two_line { 40.0 } else { 26.0 };
    egui::Frame::group(ui.style())
        .fill(c.bg)
        .stroke(Stroke::new(1.0, c.border))
        .corner_radius(CornerRadius::same(2))
        .inner_margin(Margin::symmetric(12, 10))
        .show(ui, |ui| {
            let full = ui.available_width();
            ui.allocate_ui_with_layout(
                vec2(full, row_h),
                egui::Layout::left_to_right(egui::Align::Center),
                |ui| {
                    letter_tile(ui, letter);
                    ui.add_space(12.0);
                    // Account text next to the icon. It is ONE widget (a multi-line
                    // LayoutJob) so the fixed-row Center aligns it vertically with the icon +
                    // buttons — a stacked `vertical` of two labels does not center reliably.
                    if two_line {
                        let mut job = LayoutJob::default();
                        job.append(
                            brand,
                            0.0,
                            TextFormat {
                                font_id: FontId::new(12.5, FontFamily::Name(BOLD_FAMILY.into())),
                                color: c.text,
                                ..Default::default()
                            },
                        );
                        if let Some(d) = detail {
                            job.append(
                                &format!("\n{}", elide(d, 20)),
                                0.0,
                                TextFormat {
                                    font_id: FontId::proportional(10.0),
                                    color: c.muted,
                                    ..Default::default()
                                },
                            );
                        }
                        ui.label(job);
                    } else {
                        ui.label(bold(brand, 13.0).color(c.text));
                    }
                    // Actions floated to the far right (vertically centered).
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if loading {
                            ui.add(egui::Spinner::new().size(16.0));
                        } else if signed {
                            if mini_btn(ui, tr(lang, "settings.logout"), true) {
                                act = RowAct::Logout;
                            }
                            ui.add_space(6.0);
                            if mini_btn(ui, tr(lang, "settings.switch"), false) {
                                act = RowAct::Switch;
                            }
                            ui.add_space(10.0);
                            let (dr, _) = ui.allocate_exact_size(vec2(8.0, 8.0), Sense::hover());
                            ui.painter_at(dr).circle_filled(dr.center(), 3.5, c.ok);
                        } else if mini_btn(ui, tr(lang, "settings.login"), true) {
                            act = RowAct::SignIn;
                        }
                    });
                },
            );
        });
    act
}

/// Elide a string to at most `max` chars with a trailing ellipsis.
fn elide(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        s.chars().take(max.saturating_sub(1)).collect::<String>() + "…"
    }
}

/// Shared round radio dot (used by both theme and language for a uniform style).
fn draw_radio_dot(p: &egui::Painter, center: Pos2, selected: bool) {
    let c = pal();
    let outer = if selected { c.accent } else { c.border };
    p.circle_filled(center, 9.0, outer);
    p.circle_filled(center, 7.5, c.bg);
    if selected {
        p.circle_filled(center, 4.0, c.accent);
    }
}

/// Full-width radio row: round dot + label + trailing 2-letter code (language).
fn radio_row(ui: &mut egui::Ui, label: &str, code: &str, selected: bool) -> bool {
    let c = pal();
    let resp = ui
        .horizontal(|ui| {
            let (r, _) = ui.allocate_exact_size(vec2(18.0, 18.0), Sense::hover());
            draw_radio_dot(&ui.painter_at(r), r.center(), selected);
            ui.add_space(10.0);
            ui.label(RichText::new(label).size(13.0).color(c.text));
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.label(
                    RichText::new(code)
                        .size(10.0)
                        .color(c.faint)
                        .extra_letter_spacing(1.0),
                );
            });
        })
        .response;
    resp.interact(Sense::click()).clicked()
}

/// Compact radio item: round dot + label (theme, laid out side by side). Same dot as language.
fn theme_radio(ui: &mut egui::Ui, label: &str, selected: bool) -> bool {
    let c = pal();
    let resp = ui
        .horizontal(|ui| {
            let (r, _) = ui.allocate_exact_size(vec2(18.0, 18.0), Sense::hover());
            draw_radio_dot(&ui.painter_at(r), r.center(), selected);
            ui.add_space(10.0);
            ui.label(RichText::new(label).size(13.0).color(c.text));
        })
        .response;
    resp.interact(Sense::click()).clicked()
}

pub fn render(app: &mut App, ctx: &egui::Context) -> Action {
    let mut action = Action::None;
    let granted = app.perm_granted;
    let lang = app.lang;
    let theme_choice = app.theme_choice;
    let google = app.google_account.clone();
    let facebook = app.facebook_linked;
    let linked_any = google.is_some() || facebook;
    let loading = !app.accounts_known;

    egui::CentralPanel::default()
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(pal().bg)
                .stroke(Stroke::NONE)
                .inner_margin(content_margin(16.0, 16.0)),
        )
        .show(ctx, |ui| {
            egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
                let c = pal();
                ui.add_space(6.0);

                // 01 PERMISSIONS
                let perm_right = if granted {
                    (tr(lang, "settings.granted"), c.accent)
                } else {
                    (tr(lang, "settings.not_granted"), c.accent)
                };
                section_band(ui, "01", tr(lang, "settings.permissions"), Some(perm_right));
                ui.add_space(8.0);
                egui::Frame::group(ui.style())
                    .fill(c.panel)
                    .stroke(Stroke::NONE)
                    .corner_radius(CornerRadius::same(4))
                    .inner_margin(Margin::symmetric(12, 12))
                    .show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.label(
                                    bold(tr(lang, "settings.all_files"), 12.0)
                                        .color(c.text)
                                        .extra_letter_spacing(0.5),
                                );
                                let sub = if granted {
                                    tr(lang, "settings.all_files.on")
                                } else {
                                    tr(lang, "settings.all_files.off")
                                };
                                ui.label(RichText::new(sub).size(11.0).color(c.muted));
                            });
                            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                                if granted {
                                    // Status pill: BORDER ONLY, no fill — it is status, not a
                                    // button (the touchable grant control carries the fill).
                                    egui::Frame::group(ui.style())
                                        .fill(c.bg)
                                        .stroke(Stroke::new(1.0, c.accent))
                                        .corner_radius(CornerRadius::same(2))
                                        .inner_margin(Margin::symmetric(6, 2))
                                        .show(ui, |ui| {
                                            ui.label(
                                                RichText::new(tr(lang, "settings.granted"))
                                                    .size(8.5)
                                                    .color(c.accent)
                                                    .extra_letter_spacing(0.8),
                                            );
                                        });
                                } else if ui
                                    .add(
                                        Button::new(
                                            RichText::new(tr(lang, "settings.enable"))
                                                .size(11.0)
                                                .color(c.on_accent)
                                                .extra_letter_spacing(1.0),
                                        )
                                        .fill(c.accent)
                                        .stroke(Stroke::NONE),
                                    )
                                    .clicked()
                                {
                                    action = Action::GrantPermission;
                                }
                            });
                        });
                    });

                ui.add_space(16.0);
                // 02 SIGN-IN — the LINKED/NOT-LINKED badge waits until the first fetch lands.
                let signin_right = if loading {
                    None
                } else if linked_any {
                    Some((tr(lang, "settings.linked"), c.accent))
                } else {
                    Some((tr(lang, "settings.not_linked"), c.accent))
                };
                section_band(ui, "02", tr(lang, "settings.signin"), signin_right);
                ui.add_space(8.0);
                // GOOGLE — brand endonym (not an i18n key). Signed-in detail = the email.
                match account_row(ui, "G", "Google", google.as_deref(), google.is_some(), loading, lang) {
                    RowAct::SignIn | RowAct::Switch => action = Action::SignInGoogle,
                    RowAct::Logout => action = Action::SignOutGoogle,
                    RowAct::None => {}
                }
                ui.add_space(8.0);
                // FACEBOOK — no email available, so show a generic "Connected" detail.
                let fb_detail = if facebook {
                    Some(tr(lang, "settings.connected"))
                } else {
                    None
                };
                match account_row(ui, "f", "Facebook", fb_detail, facebook, loading, lang) {
                    RowAct::SignIn | RowAct::Switch => action = Action::SignInFacebook,
                    RowAct::Logout => action = Action::SignOutFacebook,
                    RowAct::None => {}
                }

                ui.add_space(16.0);
                // 03 THEME — horizontal radio group.
                let light = theme_choice == ThemeChoice::Light;
                let theme_right = (
                    tr(lang, if light { "settings.theme.light" } else { "settings.theme.dark" }),
                    c.accent,
                );
                section_band(ui, "03", tr(lang, "settings.theme"), Some(theme_right));
                ui.add_space(8.0);
                ui.horizontal(|ui| {
                    if theme_radio(ui, tr(lang, "settings.theme.light"), light) {
                        action = Action::SetTheme(ThemeChoice::Light);
                    }
                    ui.add_space(28.0);
                    if theme_radio(ui, tr(lang, "settings.theme.dark"), !light) {
                        action = Action::SetTheme(ThemeChoice::Dark);
                    }
                });

                ui.add_space(16.0);
                // 04 LANGUAGE — same round radio style as theme.
                section_band(ui, "04", tr(lang, "settings.language"), None);
                ui.add_space(6.0);
                if radio_row(ui, "ENGLISH", "EN", lang == Lang::En) {
                    action = Action::SetLang(Lang::En);
                }
                ui.add_space(4.0);
                if radio_row(ui, "TIẾNG VIỆT", "VI", lang == Lang::Vi) {
                    action = Action::SetLang(Lang::Vi);
                }
                ui.add_space(24.0);
            });
        });

    action
}
