//! Onboarding — shown while All-Files-Access is not granted. Tapping the button opens the
//! system special-access page; on return the app re-checks and advances to the app list.
//!
//! This screen renders BEFORE permission is granted, and `app.rs` does not draw the shared
//! top/bottom nav for it — so it keeps its own bottom + central panels and applies the
//! safe-area insets itself (top cutout, side cutouts, bottom bar).

use egui::{vec2, CornerRadius, RichText, Sense, Stroke};

use super::{logo, primary_button, Action};
use crate::app::App;
use crate::i18n::tr;
use crate::theme::*;

pub fn render(app: &mut App, ctx: &egui::Context) -> Action {
    let mut action = Action::None;
    let c = pal();
    let lang = app.lang;
    let tex = app.logo.as_ref().map(|h| h.id());

    let i = insets();
    egui::TopBottomPanel::bottom("onb-bottom")
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(c.bg)
                .stroke(Stroke::NONE)
                .inner_margin(margin(22.0 + i.left, 22.0 + i.right, 18.0, 18.0 + i.bottom)),
        )
        .show(ctx, |ui| {
            // left-accent callout
            ui.horizontal(|ui| {
                let (r, _) = ui.allocate_exact_size(vec2(3.0, 40.0), Sense::hover());
                ui.painter().rect_filled(r, CornerRadius::ZERO, c.accent);
                ui.add_space(10.0);
                ui.vertical(|ui| {
                    ui.label(
                        RichText::new(tr(lang, "onb.callout.eyebrow"))
                            .size(10.0)
                            .color(c.accent)
                            .extra_letter_spacing(1.5),
                    );
                    ui.add_space(3.0);
                    ui.label(
                        RichText::new(tr(lang, "onb.callout.body"))
                            .size(11.0)
                            .color(c.muted),
                    );
                });
            });
            ui.add_space(14.0);
            if primary_button(ui, tr(lang, "onb.grant")) {
                action = Action::GrantPermission;
            }
        });

    egui::CentralPanel::default()
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(c.bg)
                .stroke(Stroke::NONE)
                .inner_margin(content_margin(22.0, 22.0)),
        )
        .show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    // Clear the top cutout (this screen has no shared top nav).
                    ui.add_space(safe_top());
                    if let Some(t) = tex {
                        logo(ui, t, 30.0);
                    }
                    ui.add_space(8.0);
                    ui.label(
                        RichText::new(tr(lang, "onb.tagline"))
                            .size(9.5)
                            .color(c.faint)
                            .extra_letter_spacing(2.5),
                    );

                    // Push the heading toward the lower-middle, adaptively (never clips copy).
                    ui.add_space(ui.available_height() * 0.28);
                    ui.label(eyebrow(tr(lang, "onb.eyebrow")));
                    ui.add_space(8.0);
                    ui.label(bold(tr(lang, "onb.title1"), 38.0).color(c.text));
                    ui.label(bold(tr(lang, "onb.title2"), 38.0).color(c.accent));
                    ui.add_space(12.0);
                    ui.label(RichText::new(tr(lang, "onb.body")).size(13.0).color(c.muted));
                });
        });

    action
}
