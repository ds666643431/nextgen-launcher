//! The one data seam. Every screen reaches data ONLY through this trait; the fake↔remote
//! swap is one factory line (see `data::build_data_source`). Blocking + object-safe on
//! purpose: it always runs on a worker thread (see `task::Tasks`), never on the egui frame.

use super::models::*;

pub trait DataSource: Send + Sync {
    // ---- auth (§7; LocalFake self-issues a session so the app runs offline) ----
    fn login(&self, creds: &Credentials, hwid: &str) -> Result<Session, DataError>;

    // ---- catalog / entitlements (the catalog drives the whole app list) ----
    fn catalog(&self) -> Result<Vec<SupportedApp>, DataError>;
    fn entitlement(&self, pkg: &str) -> Result<Entitlement, DataError>;

    // ---- launch-pipeline data (wired in §8; here so the contract is complete) ----
    fn lib_manifest(&self, pkg: &str) -> Result<LibManifest, DataError>;
    fn download_lib(&self, manifest: &LibManifest) -> Result<EncryptedBlob, DataError>;
    fn session_key(&self, pkg: &str, version: &str) -> Result<SessionKey, DataError>;
}

/// One error model both impls return, so screens route failures identically.
#[derive(Debug, Clone)]
pub enum DataError {
    Offline,
    Network(String),
    Unauthorized,
    NotEntitled(String),
    NotFound,
    Integrity,
    Server(String),
}

impl std::fmt::Display for DataError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            DataError::Offline => write!(f, "offline"),
            DataError::Network(m) => write!(f, "network error: {m}"),
            DataError::Unauthorized => write!(f, "not signed in"),
            DataError::NotEntitled(p) => write!(f, "not entitled: {p}"),
            DataError::NotFound => write!(f, "not found"),
            DataError::Integrity => write!(f, "integrity check failed"),
            DataError::Server(m) => write!(f, "server error: {m}"),
        }
    }
}
impl std::error::Error for DataError {}
