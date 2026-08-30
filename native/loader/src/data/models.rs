//! Domain models shared across the data adapter, the engine bridge, and the UI.

use super::secret::{SecretBytes, SecretString};

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Tier {
    Plus,
    Vip,
}
impl Tier {
    pub fn label(self) -> &'static str {
        match self {
            Tier::Plus => "PLUS",
            Tier::Vip => "VIP",
        }
    }
}

/// A game the launcher supports — the catalog is the SOURCE OF TRUTH for app metadata
/// (title, tier, package). The "installed" set comes from the engine; everything the UI
/// shows is a merge of this catalog with that set (never the device's own app list).
#[derive(Clone, Debug)]
pub struct SupportedApp {
    pub pkg: String,
    pub title: String,
    pub tier: Tier,
    /// Play-Store icon URL, loaded remotely by the launcher.
    pub icon_url: String,
    /// Mod-menu description shown on the App Details screen.
    pub description: String,
}

// --- auth / launch-pipeline models (defined for the full contract; used in §7/§8) ---

#[derive(Clone)]
pub struct Credentials {
    pub license_key: SecretString,
}

#[derive(Clone, Debug)]
pub struct Session {
    pub subject: String,
    pub tier: String,
    pub token: SecretBytes,
    pub expires_unix: u64,
}

#[derive(Clone, Debug)]
pub struct Entitlement {
    pub pkg: String,
    pub granted: bool,
    pub plan: String,
    pub expires_unix: Option<u64>,
}

#[derive(Clone, Debug)]
pub struct LibManifest {
    pub game_pkg: String,
    pub build_id: u32,
    pub version: String,
    pub url: String,
    pub so_sha256: [u8; 32],
    pub min_launcher: u32,
}

#[derive(Clone, Debug)]
pub struct EncryptedBlob {
    pub pkg: String,
    pub build_id: u32,
    pub bytes: Vec<u8>,
    pub so_sha256: [u8; 32],
}

#[derive(Clone)]
pub struct SessionKey {
    pub pkg: String,
    pub build_id: u32,
    pub transport_key: SecretBytes,
    pub start_secret: SecretBytes,
    pub expires_unix: u64,
}
