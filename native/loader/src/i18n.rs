//! Tiny key-based i18n. UI code references stable KEYS (never English literals); the tables
//! below resolve a key to the active language, falling back to English, then to the key
//! itself so a missing translation is visible rather than silently blank.
//!
//! Keys are `&'static str` (every call site passes a literal), so the key can be returned
//! verbatim as the last-resort fallback without allocating.

use crate::app::Lang;

/// Resolve a translation key for the active language.
pub fn tr(lang: Lang, key: &'static str) -> &'static str {
    let table: &[(&str, &str)] = match lang {
        Lang::En => EN,
        Lang::Vi => VI,
    };
    if let Some(v) = lookup(table, key) {
        return v;
    }
    // Fall back to English for a key the non-English table is missing.
    if !matches!(lang, Lang::En) {
        if let Some(v) = lookup(EN, key) {
            return v;
        }
    }
    key
}

/// Resolve a key whose value contains a `{n}` placeholder, substituting `n`.
pub fn trn(lang: Lang, key: &'static str, n: i64) -> String {
    tr(lang, key).replace("{n}", &n.to_string())
}

fn lookup(table: &[(&str, &'static str)], key: &str) -> Option<&'static str> {
    table.iter().find(|(k, _)| *k == key).map(|(_, v)| *v)
}

// ------------------------------------------------------------------- English ----------

const EN: &[(&str, &str)] = &[
    // nav / screen names
    ("nav.apps", "APPS"),
    ("nav.settings", "SETTINGS"),
    // apps list
    ("apps.section.installed", "Installed"),
    ("apps.section.supported", "Supported, not installed"),
    ("apps.count", "{n} apps"),
    ("apps.installed.empty", "No apps installed yet — pick one below to add it."),
    ("apps.supported.empty", "All supported apps are installed."),
    ("launch.downloading", "Downloading"),
    ("launch.activating", "Activating"),
    ("launch.launching", "Launching"),
    ("common.loading", "Loading…"),
    ("card.adding", "ADDING…"),
    ("badge.installed", "INSTALLED"),
    ("badge.not_installed", "NOT INSTALLED"),
    // settings
    ("settings.permissions", "Permissions"),
    ("settings.granted", "GRANTED"),
    ("settings.not_granted", "NOT GRANTED"),
    ("settings.all_files", "ALL FILES ACCESS"),
    ("settings.all_files.on", "Needed to patch, install and launch mods"),
    ("settings.all_files.off", "Mods cannot install until this is on"),
    ("settings.enable", "ENABLE"),
    ("settings.signin", "Sign-in options"),
    ("settings.linked", "LINKED"),
    ("settings.not_linked", "NOT LINKED"),
    ("settings.signin.desc", "Link an account to carry your license across devices."),
    ("settings.login.google", "Login to Google"),
    ("settings.login.facebook", "Login to Facebook"),
    ("settings.signout", "Tap to sign out"),
    ("settings.login", "LOGIN"),
    ("settings.switch", "SWITCH"),
    ("settings.logout", "LOGOUT"),
    ("settings.connected", "Connected"),
    ("settings.theme", "Theme scheme"),
    ("settings.theme.light", "LIGHT"),
    ("settings.theme.dark", "DARK"),
    ("settings.language", "Language"),
    // app details
    ("details.title", "Details"),
    ("details.back", "Back"),
    ("details.overview", "Overview"),
    ("details.package", "Package"),
    ("details.status", "Status"),
    ("details.tier", "Tier"),
    ("details.description", "Description"),
    ("details.need_install", "This game isn't installed on your device. Install it from the Play Store first, then come back to mod it."),
    ("details.status.installed", "Installed"),
    ("details.status.not_installed", "Not installed"),
    ("details.launch", "Launch"),
    ("details.install", "Install Mod"),
    ("details.installing", "Installing…"),
    ("details.uninstall", "Uninstall Mod"),
    ("details.playstore", "Play Store"),
    // onboarding
    ("onb.tagline", "A NEXT GENERATION OF GAME MODDING"),
    ("onb.eyebrow", "01 / Permission"),
    ("onb.title1", "ALL FILES"),
    ("onb.title2", "ACCESS"),
    ("onb.callout.eyebrow", "SETTINGS → SPECIAL ACCESS"),
    ("onb.callout.body", "Tapping below opens the system page. You can change it later in Settings → Permissions."),
    ("onb.body", "Android keeps mod files out of reach until you allow it. NextGEN needs full file access to patch store builds and launch them."),
    ("onb.grant", "✓  Grant all files access"),
    // toasts
    ("toast.signin", "Opening sign-in…"),
    ("toast.signout", "Signed out"),
];

// ------------------------------------------------------------------- Vietnamese -------

const VI: &[(&str, &str)] = &[
    // nav / screen names
    ("nav.apps", "ỨNG DỤNG"),
    ("nav.settings", "CÀI ĐẶT"),
    // apps list
    ("apps.section.installed", "Đã cài đặt"),
    ("apps.section.supported", "Được hỗ trợ, chưa cài"),
    ("apps.count", "{n} ứng dụng"),
    ("apps.installed.empty", "Chưa cài ứng dụng nào — chọn bên dưới để thêm."),
    ("apps.supported.empty", "Tất cả ứng dụng hỗ trợ đã được cài."),
    ("launch.downloading", "Đang tải"),
    ("launch.activating", "Đang kích hoạt"),
    ("launch.launching", "Đang khởi chạy"),
    ("common.loading", "Đang tải…"),
    ("card.adding", "ĐANG THÊM…"),
    ("badge.installed", "ĐÃ CÀI"),
    ("badge.not_installed", "CHƯA CÀI"),
    // settings
    ("settings.permissions", "Quyền"),
    ("settings.granted", "ĐÃ CẤP"),
    ("settings.not_granted", "CHƯA CẤP"),
    ("settings.all_files", "TRUY CẬP MỌI TỆP"),
    ("settings.all_files.on", "Cần để vá, cài đặt và khởi chạy mod"),
    ("settings.all_files.off", "Không thể cài mod cho đến khi bật"),
    ("settings.enable", "BẬT"),
    ("settings.signin", "Tùy chọn đăng nhập"),
    ("settings.linked", "ĐÃ LIÊN KẾT"),
    ("settings.not_linked", "CHƯA LIÊN KẾT"),
    ("settings.signin.desc", "Liên kết tài khoản để mang giấy phép của bạn qua các thiết bị."),
    ("settings.login.google", "Đăng nhập Google"),
    ("settings.login.facebook", "Đăng nhập Facebook"),
    ("settings.signout", "Nhấn để đăng xuất"),
    ("settings.login", "ĐĂNG NHẬP"),
    ("settings.switch", "ĐỔI"),
    ("settings.logout", "ĐĂNG XUẤT"),
    ("settings.connected", "Đã kết nối"),
    ("settings.theme", "Giao diện"),
    ("settings.theme.light", "SÁNG"),
    ("settings.theme.dark", "TỐI"),
    ("settings.language", "Ngôn ngữ"),
    // app details
    ("details.title", "Chi tiết"),
    ("details.back", "Quay lại"),
    ("details.overview", "Tổng quan"),
    ("details.package", "Gói"),
    ("details.status", "Trạng thái"),
    ("details.tier", "Hạng"),
    ("details.description", "Mô tả"),
    ("details.need_install", "Trò chơi này chưa được cài trên thiết bị. Hãy cài từ Play Store trước, rồi quay lại để mod."),
    ("details.status.installed", "Đã cài"),
    ("details.status.not_installed", "Chưa cài"),
    ("details.launch", "Khởi chạy"),
    ("details.install", "Cài mod"),
    ("details.installing", "Đang cài…"),
    ("details.uninstall", "Gỡ mod"),
    ("details.playstore", "Play Store"),
    // onboarding
    ("onb.tagline", "THẾ HỆ MỚI CỦA MOD GAME"),
    ("onb.eyebrow", "01 / Quyền"),
    ("onb.title1", "TRUY CẬP"),
    ("onb.title2", "MỌI TỆP"),
    ("onb.callout.eyebrow", "CÀI ĐẶT → TRUY CẬP ĐẶC BIỆT"),
    ("onb.callout.body", "Nhấn bên dưới để mở trang hệ thống. Bạn có thể thay đổi sau trong Cài đặt → Quyền."),
    ("onb.body", "Android chặn các tệp mod cho đến khi bạn cho phép. NextGEN cần quyền truy cập tệp đầy đủ để vá bản dựng và khởi chạy."),
    ("onb.grant", "✓  Cấp quyền truy cập mọi tệp"),
    // toasts
    ("toast.signin", "Đang mở đăng nhập…"),
    ("toast.signout", "Đã đăng xuất"),
];
