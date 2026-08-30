//! Theme: a runtime-switchable palette (light / dark), the Be Vietnam Pro font stack, and
//! the recurring text helpers. Colors are read through `pal()` (a thread-local `Palette`)
//! so a light↔dark toggle is a single `apply()` with no call-site churn. `bold()` uses a
//! real semibold face (egui's `.strong()` only changes color, not weight).

use std::cell::Cell;
use std::sync::Arc;

use egui::{Color32, FontFamily, FontId, RichText};

use crate::app::ThemeChoice;

/// Fallback top padding used only when the real safe-area insets can't be read
/// (e.g. the JNI context isn't an Activity). Real insets override this — see [`insets`].
pub const SAFE_TOP: f32 = 30.0;

// ------------------------------------------------------------------- safe-area insets --

/// Display safe-area insets in **egui points** (camera cutout ∪ system bars), refreshed
/// from Android each throttled tick. Read on the UI thread via [`insets`] / [`safe_top`].
#[derive(Clone, Copy, Default)]
pub struct Insets {
    pub top: f32,
    pub left: f32,
    pub right: f32,
    pub bottom: f32,
}

thread_local! {
    static INSETS: Cell<Insets> = const {
        Cell::new(Insets { top: SAFE_TOP, left: 0.0, right: 0.0, bottom: 0.0 })
    };
}

pub fn set_insets(i: Insets) {
    INSETS.with(|c| c.set(i));
}
pub fn insets() -> Insets {
    INSETS.with(|c| c.get())
}
pub fn safe_top() -> f32 {
    insets().top
}
pub fn safe_bottom() -> f32 {
    insets().bottom
}

fn clamp_i8(v: f32) -> i8 {
    v.round().clamp(0.0, 127.0) as i8
}

/// Build a `Margin` from point values (clamped to egui's i8 range).
pub fn margin(l: f32, r: f32, t: f32, b: f32) -> egui::Margin {
    egui::Margin {
        left: clamp_i8(l),
        right: clamp_i8(r),
        top: clamp_i8(t),
        bottom: clamp_i8(b),
    }
}

/// Horizontal/vertical content margin that also clears the left/right safe-area insets
/// (so content doesn't slide under a side cutout in landscape).
pub fn content_margin(h: f32, v: f32) -> egui::Margin {
    let i = insets();
    margin(h + i.left, h + i.right, v, v)
}

// ------------------------------------------------------------------- palette ----------

#[derive(Clone, Copy)]
pub struct Palette {
    pub bg: Color32,
    pub band: Color32,
    pub panel: Color32,
    pub text: Color32,
    pub muted: Color32,
    pub faint: Color32,
    pub accent: Color32,
    pub border: Color32,
    pub tile: Color32,
    pub nav_bg: Color32,
    pub nav_inactive: Color32,
    pub on_accent: Color32,
    pub ok: Color32,
    /// Warning accent (text/stroke/icon) + its soft fill, for the warning banner.
    pub warn: Color32,
    pub warn_bg: Color32,
    /// Destructive accent (e.g. the Uninstall button).
    pub danger: Color32,
    pub dark: bool,
}

impl Palette {
    pub const fn light() -> Self {
        Self {
            bg: Color32::from_rgb(0xFF, 0xFF, 0xFF),
            band: Color32::from_rgb(0xEE, 0xF0, 0xF2),
            panel: Color32::from_rgb(0xF4, 0xF5, 0xF7),
            text: Color32::from_rgb(0x16, 0x19, 0x1D),
            muted: Color32::from_rgb(0x6B, 0x72, 0x80),
            faint: Color32::from_rgb(0x9A, 0xA1, 0xAB),
            accent: Color32::from_rgb(0x1B, 0x8D, 0xF0),
            border: Color32::from_rgb(0xDA, 0xDE, 0xE3),
            tile: Color32::from_rgb(0x17, 0x1A, 0x20),
            nav_bg: Color32::from_rgb(0x10, 0x13, 0x18),
            nav_inactive: Color32::from_rgb(0x8A, 0x92, 0x9E),
            on_accent: Color32::WHITE,
            ok: Color32::from_rgb(0x1E, 0xA1, 0x5A),
            warn: Color32::from_rgb(0x92, 0x63, 0x00),
            warn_bg: Color32::from_rgb(0xFF, 0xF3, 0xCD),
            danger: Color32::from_rgb(0xD1, 0x3A, 0x3A),
            dark: false,
        }
    }

    pub const fn dark() -> Self {
        Self {
            bg: Color32::from_rgb(0x0E, 0x11, 0x16),
            band: Color32::from_rgb(0x18, 0x1D, 0x24),
            panel: Color32::from_rgb(0x14, 0x18, 0x1E),
            text: Color32::from_rgb(0xE7, 0xEA, 0xEE),
            muted: Color32::from_rgb(0x97, 0xA0, 0xAD),
            faint: Color32::from_rgb(0x66, 0x6E, 0x7A),
            accent: Color32::from_rgb(0x3A, 0x9E, 0xF5),
            border: Color32::from_rgb(0x2A, 0x30, 0x39),
            tile: Color32::from_rgb(0x2A, 0x30, 0x3A),
            nav_bg: Color32::from_rgb(0x08, 0x0B, 0x0F),
            nav_inactive: Color32::from_rgb(0x6B, 0x72, 0x80),
            on_accent: Color32::WHITE,
            ok: Color32::from_rgb(0x3D, 0xC1, 0x7B),
            warn: Color32::from_rgb(0xF2, 0xC0, 0x4D),
            warn_bg: Color32::from_rgb(0x38, 0x2C, 0x10),
            danger: Color32::from_rgb(0xF2, 0x6D, 0x6D),
            dark: true,
        }
    }

    pub const fn of(choice: ThemeChoice) -> Self {
        match choice {
            ThemeChoice::Light => Self::light(),
            ThemeChoice::Dark => Self::dark(),
        }
    }
}

thread_local! {
    static PAL: Cell<Palette> = const { Cell::new(Palette::light()) };
}

/// The palette in effect on the UI thread. Cheap (a `Cell` read); safe to call per-widget.
pub fn pal() -> Palette {
    PAL.with(|c| c.get())
}

// ------------------------------------------------------------------- fonts ------------

const FONT_REGULAR: &[u8] = include_bytes!("../assets/fonts/BeVietnamPro-Regular.ttf");
const FONT_MEDIUM: &[u8] = include_bytes!("../assets/fonts/BeVietnamPro-Medium.ttf");
const FONT_SEMIBOLD: &[u8] = include_bytes!("../assets/fonts/BeVietnamPro-SemiBold.ttf");

/// The named family used by [`bold`] for real semibold headings.
pub const BOLD_FAMILY: &str = "bvp-bold";

fn install_fonts(ctx: &egui::Context) {
    use egui::{FontData, FontDefinitions};
    let mut fonts = FontDefinitions::default(); // keeps egui built-ins as glyph fallbacks

    fonts
        .font_data
        .insert("bvp-regular".into(), Arc::new(FontData::from_static(FONT_REGULAR)));
    fonts
        .font_data
        .insert("bvp-medium".into(), Arc::new(FontData::from_static(FONT_MEDIUM)));
    fonts
        .font_data
        .insert("bvp-semibold".into(), Arc::new(FontData::from_static(FONT_SEMIBOLD)));

    // Body: Be Vietnam Pro Regular first, then the egui defaults (Latin fallback + emoji +
    // the symbol glyphs the UI leans on, e.g. ◈ ⏻ → ✓).
    if let Some(prop) = fonts.families.get_mut(&FontFamily::Proportional) {
        prop.insert(0, "bvp-regular".into());
        prop.insert(1, "bvp-medium".into());
    }

    // Headings: a dedicated semibold family (egui does not synthesise weight).
    let mut bold_chain = vec!["bvp-semibold".to_string()];
    if let Some(def) = fonts.families.get(&FontFamily::Proportional) {
        bold_chain.extend(def.iter().cloned());
    }
    fonts
        .families
        .insert(FontFamily::Name(BOLD_FAMILY.into()), bold_chain);

    ctx.set_fonts(fonts);
}

// ------------------------------------------------------------------- apply ------------

/// One-time setup: fonts + spacing + the initial (light) palette & visuals.
pub fn install(ctx: &egui::Context) {
    install_fonts(ctx);

    let mut s = (*ctx.style()).clone();
    s.spacing.item_spacing = egui::vec2(8.0, 8.0);
    s.spacing.button_padding = egui::vec2(12.0, 8.0);
    s.spacing.window_margin = egui::Margin::same(0);
    ctx.set_style(s);

    apply(ctx, ThemeChoice::Light);
}

/// Switch the live palette + egui visuals. Call whenever the theme choice changes.
pub fn apply(ctx: &egui::Context, choice: ThemeChoice) {
    let p = Palette::of(choice);
    PAL.with(|c| c.set(p));

    let mut v = if p.dark {
        egui::Visuals::dark()
    } else {
        egui::Visuals::light()
    };
    v.panel_fill = p.bg;
    v.window_fill = p.bg;
    v.extreme_bg_color = p.panel;
    v.faint_bg_color = p.band;
    v.override_text_color = Some(p.text);
    v.selection.bg_fill = p.accent.linear_multiply(0.25);
    v.hyperlink_color = p.accent;
    v.widgets.noninteractive.bg_stroke = egui::Stroke::new(1.0, p.border);
    v.widgets.inactive.bg_stroke = egui::Stroke::new(1.0, p.border);
    v.widgets.hovered.bg_stroke = egui::Stroke::new(1.0, p.accent);
    ctx.set_visuals(v);
}

// ------------------------------------------------------------------- text helpers -----

/// A real-weight (semibold) heading. `size` is baked in; chain `.color()` etc. after.
pub fn bold(text: impl Into<String>, size: f32) -> RichText {
    RichText::new(text).font(FontId::new(size, FontFamily::Name(BOLD_FAMILY.into())))
}

/// Uppercase, letter-spaced label (the recurring "01 / PERMISSION" style).
pub fn tracked(text: &str, size: f32, color: Color32, spacing: f32) -> RichText {
    RichText::new(text.to_uppercase())
        .size(size)
        .color(color)
        .extra_letter_spacing(spacing)
}

/// Small uppercase eyebrow in the accent color.
pub fn eyebrow(text: &str) -> RichText {
    tracked(text, 11.0, pal().accent, 2.0)
}

/// Two-letter monogram: initials of the first two words (Frontier Ops → "FO"), or the
/// first two letters of a single word (KXBYTE → "KX").
pub fn monogram_of(title: &str) -> String {
    let words: Vec<&str> = title.split_whitespace().collect();
    let s: String = if words.len() >= 2 {
        [words[0], words[1]]
            .iter()
            .filter_map(|w| w.chars().next())
            .collect()
    } else {
        title.chars().filter(|c| c.is_alphanumeric()).take(2).collect()
    };
    s.to_uppercase()
}
