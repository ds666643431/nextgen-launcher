//! App details — reached by tapping an app card. A full-width banner (icon + app name; a
//! real banner image will live here later) flush under the top nav, then a compact info
//! table: the package id is a Play-Store LINK (opens the store), status is a badge, plus
//! tier and a wrapping description. If the game isn't installed on the DEVICE, a warning bar
//! asks the user to install it from the Play Store first and the mod actions are hidden.
//! Bottom bar: installed → Launch + Uninstall Mod on one row; not installed → Install Mod.

use egui::{
    pos2, vec2, Align2, Color32, CornerRadius, FontFamily, FontId, Margin, Rect, RichText, Sense,
    Stroke,
};

use super::{status_badge, Action};
use crate::app::{App, Lang};
use crate::i18n::tr;
use crate::theme::*;

#[derive(Clone, Copy)]
enum Icon {
    Launch,
    Install,
    Uninstall,
}

#[derive(Clone, Copy)]
enum BtnStyle {
    Primary,
    Danger,
}

fn draw_icon(p: &egui::Painter, icon: &Icon, c: egui::Pos2, col: Color32) {
    let (cx, cy) = (c.x, c.y);
    let st = Stroke::new(2.0, col);
    match icon {
        Icon::Launch => {
            p.add(egui::Shape::convex_polygon(
                vec![
                    pos2(cx - 6.0, cy - 7.0),
                    pos2(cx - 6.0, cy + 7.0),
                    pos2(cx + 7.0, cy),
                ],
                col,
                Stroke::NONE,
            ));
        }
        Icon::Install => {
            p.line_segment([pos2(cx, cy - 7.0), pos2(cx, cy + 3.0)], st);
            p.line_segment([pos2(cx - 4.0, cy - 1.0), pos2(cx, cy + 3.0)], st);
            p.line_segment([pos2(cx + 4.0, cy - 1.0), pos2(cx, cy + 3.0)], st);
            p.line_segment([pos2(cx - 6.0, cy + 7.0), pos2(cx + 6.0, cy + 7.0)], st);
        }
        Icon::Uninstall => {
            p.line_segment([pos2(cx - 7.0, cy - 5.0), pos2(cx + 7.0, cy - 5.0)], st);
            p.line_segment([pos2(cx - 2.5, cy - 5.0), pos2(cx - 2.5, cy - 7.5)], st);
            p.line_segment([pos2(cx + 2.5, cy - 5.0), pos2(cx + 2.5, cy - 7.5)], st);
            p.line_segment([pos2(cx - 2.5, cy - 7.5), pos2(cx + 2.5, cy - 7.5)], st);
            p.line_segment([pos2(cx - 5.0, cy - 5.0), pos2(cx - 4.0, cy + 7.5)], st);
            p.line_segment([pos2(cx + 5.0, cy - 5.0), pos2(cx + 4.0, cy + 7.5)], st);
            p.line_segment([pos2(cx - 4.0, cy + 7.5), pos2(cx + 4.0, cy + 7.5)], st);
        }
    }
}

/// "Open in new window" glyph — a box with an arrow leaving the top-right corner.
fn ext_link_icon(p: &egui::Painter, c: egui::Pos2, col: Color32) {
    let (cx, cy) = (c.x, c.y);
    let st = Stroke::new(1.5, col);
    // box (bottom-left, open top-right corner)
    p.line_segment([pos2(cx - 5.0, cy - 2.0), pos2(cx - 5.0, cy + 5.0)], st);
    p.line_segment([pos2(cx - 5.0, cy + 5.0), pos2(cx + 3.0, cy + 5.0)], st);
    p.line_segment([pos2(cx + 3.0, cy + 5.0), pos2(cx + 3.0, cy + 1.0)], st);
    p.line_segment([pos2(cx - 5.0, cy - 2.0), pos2(cx - 1.0, cy - 2.0)], st);
    // arrow out to the top-right
    p.line_segment([pos2(cx - 1.0, cy + 1.0), pos2(cx + 5.0, cy - 5.0)], st);
    p.line_segment([pos2(cx + 1.0, cy - 5.0), pos2(cx + 5.0, cy - 5.0)], st);
    p.line_segment([pos2(cx + 5.0, cy - 5.0), pos2(cx + 5.0, cy - 1.0)], st);
}

/// Warning triangle with an exclamation mark.
fn warn_icon(p: &egui::Painter, c: egui::Pos2, tri: Color32, mark: Color32) {
    let (cx, cy) = (c.x, c.y);
    p.add(egui::Shape::convex_polygon(
        vec![
            pos2(cx, cy - 8.0),
            pos2(cx - 8.0, cy + 7.0),
            pos2(cx + 8.0, cy + 7.0),
        ],
        tri,
        Stroke::NONE,
    ));
    p.line_segment([pos2(cx, cy - 2.0), pos2(cx, cy + 2.5)], Stroke::new(2.0, mark));
    p.circle_filled(pos2(cx, cy + 5.0), 1.3, mark);
}

/// Full-width (or column-width) action button: real icon + label, centered as a group.
fn action_button(ui: &mut egui::Ui, icon: Icon, label: &str, style: BtnStyle) -> bool {
    let c = pal();
    let (rect, resp) = ui.allocate_exact_size(vec2(ui.available_width(), 46.0), Sense::click());
    let (fill, fg, stroke) = match style {
        BtnStyle::Primary => (c.accent, c.on_accent, Stroke::NONE),
        BtnStyle::Danger => (c.bg, c.danger, Stroke::new(1.0, c.danger)),
    };
    let p = ui.painter_at(rect);
    p.rect_filled(rect, CornerRadius::same(4), fill);
    if stroke.width > 0.0 {
        p.rect_stroke(rect, CornerRadius::same(4), stroke, egui::StrokeKind::Inside);
    }
    let icon_w = 20.0;
    let gap = 10.0;
    let font = FontId::new(13.0, FontFamily::Name(BOLD_FAMILY.into()));
    let shift = (icon_w + gap) / 2.0;
    let trect = p.text(
        pos2(rect.center().x + shift, rect.center().y),
        Align2::CENTER_CENTER,
        label.to_uppercase(),
        font,
        fg,
    );
    let icon_c = pos2(trect.left() - gap - icon_w / 2.0, rect.center().y);
    draw_icon(&p, &icon, icon_c, fg);
    resp.clicked()
}

/// Package row whose value is a Play-Store link (accent + underline + "open new window" icon).
fn link_row(ui: &mut egui::Ui, label: &str, value: &str) -> bool {
    let c = pal();
    let mut clicked = false;
    ui.add_space(4.0);
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(label.to_uppercase())
                .size(10.5)
                .color(c.muted)
                .extra_letter_spacing(1.5),
        );
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            // Tight, fixed gap between the text and the icon (the default item spacing here
            // is what made the gap look large).
            ui.spacing_mut().item_spacing.x = 4.0;
            // Icon FIRST so it sits at the far right (end of the link), text to its left.
            let (ir, iresp) = ui.allocate_exact_size(vec2(13.0, 13.0), Sense::click());
            ext_link_icon(&ui.painter_at(ir), ir.center(), c.accent);
            // No truncate: the label sizes to its content so the icon sits right after the text.
            let lresp = ui.add(
                egui::Label::new(RichText::new(value).size(13.0).color(c.accent).underline())
                    .sense(Sense::click()),
            );
            if iresp.clicked() || lresp.clicked() {
                clicked = true;
            }
        });
    });
    ui.add_space(4.0);
    ui.separator();
    clicked
}

pub fn kv(ui: &mut egui::Ui, label: &str, value: &str) {
    let c = pal();
    ui.add_space(4.0);
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(label.to_uppercase())
                .size(10.5)
                .color(c.muted)
                .extra_letter_spacing(1.5),
        );
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            ui.add(egui::Label::new(bold(value, 13.0).color(c.text)).truncate());
        });
    });
    ui.add_space(4.0);
    ui.separator();
}

fn status_row(ui: &mut egui::Ui, label: &str, installed: bool, lang: Lang) {
    let c = pal();
    ui.add_space(4.0);
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(label.to_uppercase())
                .size(10.5)
                .color(c.muted)
                .extra_letter_spacing(1.5),
        );
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            status_badge(ui, installed, lang);
        });
    });
    ui.add_space(4.0);
    ui.separator();
}

/// Description row: label on top, then the (possibly long) text left-aligned below it.
fn desc_row(ui: &mut egui::Ui, label: &str, value: &str) {
    let c = pal();
    ui.add_space(8.0);
    ui.label(
        RichText::new(label.to_uppercase())
            .size(10.5)
            .color(c.muted)
            .extra_letter_spacing(1.5),
    );
    ui.add_space(5.0);
    ui.label(RichText::new(value).size(12.5).color(c.text));
    ui.add_space(6.0);
}

pub fn render(app: &mut App, ctx: &egui::Context) -> Action {
    let mut action = Action::None;
    let lang = app.lang;
    let pkg = app.detail.clone().unwrap_or_default();
    let info = app.catalog.iter().find(|a| a.pkg == pkg).cloned();
    let has = info.is_some();
    let installed = app.installed.contains(&pkg);
    let adding = app.adding.contains(&pkg);
    // Known-not-on-device → warn + hide mod actions (only the Play-Store link remains).
    let need_install = app.device_known && !app.device_pkgs.contains(&pkg);
    let play_url = format!("market://details?id={pkg}");

    // Bottom action bar — only when a mod action is possible (on device).
    if has && !need_install {
        egui::TopBottomPanel::bottom("details-bottom")
            .frame(
                egui::Frame::group(&ctx.style())
                    .fill(pal().bg)
                    .stroke(Stroke::NONE)
                    .inner_margin(content_margin(16.0, 14.0)),
            )
            .show(ctx, |ui| {
                if installed {
                    ui.columns(2, |cols| {
                        if action_button(&mut cols[0], Icon::Launch, tr(lang, "details.launch"), BtnStyle::Primary) {
                            action = Action::Launch(pkg.clone());
                        }
                        if action_button(&mut cols[1], Icon::Uninstall, tr(lang, "details.uninstall"), BtnStyle::Danger) {
                            action = Action::Uninstall(pkg.clone());
                        }
                    });
                } else {
                    let label = if adding {
                        tr(lang, "details.installing")
                    } else {
                        tr(lang, "details.install")
                    };
                    if action_button(ui, Icon::Install, label, BtnStyle::Primary) && !adding {
                        action = Action::Add(pkg.clone());
                    }
                }
            });
    }

    egui::CentralPanel::default()
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(pal().bg)
                .stroke(Stroke::NONE)
                .inner_margin(Margin::same(0)),
        )
        .show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    let c = pal();
                    let i = insets();

                    let Some(a) = info else {
                        ui.add_space(16.0);
                        ui.label(RichText::new(tr(lang, "common.loading")).color(c.muted));
                        return;
                    };

                    // Full-width banner (edge to edge), flush under the top nav, with a
                    // bottom→top black gradient so a future banner image keeps the text
                    // readable. Rounded icon.
                    let bh = 88.0;
                    let (brect, _) =
                        ui.allocate_exact_size(vec2(ui.available_width(), bh), Sense::hover());
                    let icon_rect =
                        Rect::from_min_size(pos2(brect.left() + 16.0, brect.center().y - 26.0), vec2(52.0, 52.0));
                    {
                        let p = ui.painter_at(brect);
                        p.rect_filled(brect, CornerRadius::ZERO, c.accent);
                        // black gradient: transparent at top → dark at the bottom
                        let mut mesh = egui::Mesh::default();
                        let clear = Color32::from_rgba_premultiplied(0, 0, 0, 0);
                        let dark = Color32::from_rgba_premultiplied(0, 0, 0, 150);
                        mesh.colored_vertex(brect.left_top(), clear);
                        mesh.colored_vertex(brect.right_top(), clear);
                        mesh.colored_vertex(brect.left_bottom(), dark);
                        mesh.colored_vertex(brect.right_bottom(), dark);
                        mesh.add_triangle(0, 1, 2);
                        mesh.add_triangle(1, 2, 3);
                        p.add(egui::Shape::mesh(mesh));
                        // monogram fallback tile (remote icon is painted after, on top)
                        if a.icon_url.is_empty() {
                            p.rect_filled(icon_rect, CornerRadius::same(12), c.on_accent);
                            p.text(
                                icon_rect.center(),
                                Align2::CENTER_CENTER,
                                monogram_of(&a.title),
                                FontId::proportional(18.0),
                                c.text,
                            );
                        }
                        // title on top of the gradient
                        p.text(
                            pos2(icon_rect.right() + 14.0, brect.center().y),
                            Align2::LEFT_CENTER,
                            &a.title,
                            FontId::new(22.0, FontFamily::Name(BOLD_FAMILY.into())),
                            Color32::WHITE,
                        );
                    }
                    if !a.icon_url.is_empty() {
                        egui::Image::new(a.icon_url.as_str())
                            .maintain_aspect_ratio(true)
                            .corner_radius(CornerRadius::same(12))
                            .paint_at(ui, icon_rect);
                    }

                    // Content below the banner gets a small side padding.
                    egui::Frame::group(ui.style())
                        .fill(c.bg)
                        .stroke(Stroke::NONE)
                        .corner_radius(CornerRadius::ZERO)
                        .inner_margin(margin(10.0 + i.left, 10.0 + i.right, 0.0, 0.0))
                        .show(ui, |ui| {
                            ui.set_width(ui.available_width());

                            // Warning bar when the game isn't on the device.
                            if need_install {
                                ui.add_space(12.0);
                                egui::Frame::group(ui.style())
                                    .fill(c.warn_bg)
                                    .stroke(Stroke::new(1.0, c.warn))
                                    .corner_radius(CornerRadius::same(4))
                                    .inner_margin(Margin::symmetric(12, 12))
                                    .show(ui, |ui| {
                                        ui.set_width(ui.available_width());
                                        ui.horizontal(|ui| {
                                            let (ir, _) = ui
                                                .allocate_exact_size(vec2(22.0, 22.0), Sense::hover());
                                            warn_icon(&ui.painter_at(ir), ir.center(), c.warn, c.warn_bg);
                                            ui.add_space(10.0);
                                            ui.label(
                                                RichText::new(tr(lang, "details.need_install"))
                                                    .size(12.5)
                                                    .color(c.warn),
                                            );
                                        });
                                    });
                            }

                            ui.add_space(6.0);
                            // Package id → Play-Store link.
                            if link_row(ui, tr(lang, "details.package"), &a.pkg) {
                                action = Action::OpenUrl(play_url.clone());
                            }
                            status_row(ui, tr(lang, "details.status"), installed, lang);
                            // Tier row is rendered by a plugin (plugins/tier/tier.rs) —
                            // the core/free build shows no tier row unless it is registered.
                            crate::plugin::fire_render(
                                crate::plugin::Slot::AppDetailsBeforeDescription,
                                ui,
                                &crate::plugin::RenderCtx { app: &a, lang },
                            );
                            desc_row(ui, tr(lang, "details.description"), &a.description);
                        });
                });
        });

    action
}
