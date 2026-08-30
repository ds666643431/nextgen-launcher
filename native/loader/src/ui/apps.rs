//! App list: two sections — INSTALLED (cloned) and SUPPORTED, NOT INSTALLED. Both are
//! driven by the DataSource CATALOG merged with the engine's installed set — never the
//! device's own app list. Tap an Installed card to launch; tap a Supported card to add
//! (clone) it. The logo + screen name live in the shared top bar (rendered by app.rs);
//! this screen renders only its CentralPanel.

use std::collections::HashSet;

use egui::{RichText, Stroke};

use super::{app_card, section_band, Action, CardView};
use crate::app::{App, Lang};
use crate::data::models::SupportedApp;
use crate::i18n::{tr, trn};
use crate::theme::*;

fn grid(
    ui: &mut egui::Ui,
    list: &[SupportedApp],
    adding: &HashSet<String>,
    installed: bool,
    lang: Lang,
) -> Option<String> {
    let mut tapped = None;
    for chunk in list.chunks(2) {
        ui.columns(2, |cols| {
            for (i, a) in chunk.iter().enumerate() {
                let cv = CardView {
                    monogram: monogram_of(&a.title),
                    pkg: &a.pkg,
                    title: &a.title,
                    installed,
                    adding: adding.contains(&a.pkg),
                    lang,
                    icon_url: &a.icon_url,
                    app: a,
                };
                if app_card(&mut cols[i], &cv) {
                    tapped = Some(a.pkg.clone());
                }
            }
        });
        ui.separator(); // faint row rule, matching the design's light grid
    }
    tapped
}

/// Centered muted message for an empty section.
fn empty_msg(ui: &mut egui::Ui, msg: &str) {
    let c = pal();
    ui.add_space(14.0);
    ui.vertical_centered(|ui| {
        ui.label(RichText::new(msg).size(12.0).color(c.muted));
    });
    ui.add_space(14.0);
}

pub fn render(app: &mut App, ctx: &egui::Context) -> Action {
    let mut action = Action::None;

    let lang = app.lang;
    let catalog = app.catalog.clone();
    let installed = app.installed.clone();
    let adding = app.adding.clone();
    let loading = app.loading_catalog;

    let inst: Vec<SupportedApp> = catalog
        .iter()
        .filter(|a| installed.contains(&a.pkg))
        .cloned()
        .collect();
    let supp: Vec<SupportedApp> = catalog
        .iter()
        .filter(|a| !installed.contains(&a.pkg))
        .cloned()
        .collect();

    let c = pal();

    egui::CentralPanel::default()
        .frame(
            egui::Frame::group(&ctx.style())
                .fill(c.bg)
                .stroke(Stroke::NONE)
                .inner_margin(content_margin(12.0, 0.0)),
        )
        .show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    ui.add_space(10.0);

                    // Installed
                    section_band(
                        ui,
                        "01",
                        tr(lang, "apps.section.installed"),
                        Some((&trn(lang, "apps.count", inst.len() as i64), c.muted)),
                    );
                    ui.add_space(6.0);
                    if loading && inst.is_empty() {
                        ui.horizontal(|ui| {
                            ui.spinner();
                            ui.label(RichText::new(tr(lang, "common.loading")).color(c.muted));
                        });
                    } else if inst.is_empty() {
                        empty_msg(ui, tr(lang, "apps.installed.empty"));
                    }
                    if let Some(pkg) = grid(ui, &inst, &adding, true, lang) {
                        action = Action::OpenDetail(pkg);
                    }

                    ui.add_space(8.0);

                    // Supported, not installed
                    section_band(
                        ui,
                        "02",
                        tr(lang, "apps.section.supported"),
                        Some((&trn(lang, "apps.count", supp.len() as i64), c.muted)),
                    );
                    ui.add_space(6.0);
                    if supp.is_empty() && !loading {
                        empty_msg(ui, tr(lang, "apps.supported.empty"));
                    }
                    if let Some(pkg) = grid(ui, &supp, &adding, false, lang) {
                        action = Action::OpenDetail(pkg);
                    }
                    ui.add_space(24.0);
                });
        });

    action
}
