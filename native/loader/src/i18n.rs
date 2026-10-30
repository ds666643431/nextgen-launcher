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
        Lang::Zh => ZH,
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

// ------------------------------------------------------------------- 中文 ----------

const ZH: &[(&str, &str)] = &[
    ("nav.apps", "应用"),
    ("nav.settings", "设置"),
    ("apps.section.installed", "已安装"),
    ("apps.section.supported", "支持但未安装"),
    ("apps.count", "{n} 个应用"),
    ("apps.installed.empty", "还没有安装任何应用 —— 从下面选一个添加。"),
    ("apps.supported.empty", "所有支持的应用都已安装。"),
    ("launch.downloading", "下载中"),
    ("launch.activating", "激活中"),
    ("launch.launching", "启动中"),
    ("common.loading", "加载中…"),
    ("card.adding", "添加中…"),
    ("badge.installed", "已安装"),
    ("badge.not_installed", "未安装"),
    ("settings.permissions", "权限"),
    ("settings.granted", "已授权"),
    ("settings.not_granted", "未授权"),
    ("settings.all_files", "所有文件访问权限"),
    ("settings.all_files.on", "用于修补、安装和启动模组"),
    ("settings.all_files.off", "未开启时无法安装模组"),
    ("settings.enable", "启用"),
    ("settings.signin", "登录选项"),
    ("settings.linked", "已绑定"),
    ("settings.not_linked", "未绑定"),
    ("settings.signin.desc", "绑定账号后可在多台设备间携带授权。"),
    ("settings.login.google", "登录 Google"),
    ("settings.login.facebook", "登录 Facebook"),
    ("settings.signout", "点击退出登录"),
    ("settings.login", "登录"),
    ("settings.switch", "切换"),
    ("settings.logout", "退出"),
    ("settings.connected", "已连接"),
    ("settings.theme", "主题配色"),
    ("settings.theme.light", "浅色"),
    ("settings.theme.dark", "深色"),
    ("settings.language", "语言"),
    ("details.title", "详情"),
    ("details.back", "返回"),
    ("details.overview", "概览"),
    ("details.package", "包名"),
    ("details.status", "状态"),
    ("details.tier", "等级"),
    ("details.description", "描述"),
    ("details.need_install", "本机尚未安装此游戏。请先从应用商店安装，再回来制作模组。"),
    ("details.status.installed", "已安装"),
    ("details.status.not_installed", "未安装"),
    ("details.launch", "启动"),
    ("details.install", "安装模组"),
    ("details.installing", "安装中…"),
    ("details.uninstall", "卸载模组"),
    ("details.playstore", "应用商店"),
    ("onb.tagline", "次世代游戏模组平台"),
    ("onb.eyebrow", "01 / 权限"),
    ("onb.title1", "所有文件"),
    ("onb.title2", "访问权限"),
    ("onb.callout.eyebrow", "设置 → 特殊权限"),
    ("onb.callout.body", "点击下方按钮会打开系统页面。之后可在 设置 → 权限 中随时修改。"),
    ("onb.body", "Android 默认不允许访问模组文件。NextGEN 需要完整文件访问权限才能修补商店版本并启动它们。"),
    ("onb.grant", "✓  授予所有文件访问权限"),
    ("toast.signin", "正在打开登录…"),
    ("toast.signout", "已退出登录"),
];
