//! RemoteDataSource — server-backed adapter. Wired in M5 (ureq + rustls[ring] +
//! webpki-roots per server-plan.md). Compiled ONLY under `--features remote`, so the
//! default (fake) build stays offline-buildable and pulls no networking crates.

#![cfg(feature = "remote")]

use super::models::*;
use super::source::{DataError, DataSource};

pub struct RemoteDataSource {
    base_url: String,
}

impl RemoteDataSource {
    pub fn new(base_url: &str) -> Self {
        Self {
            base_url: base_url.to_owned(),
        }
    }
}

impl DataSource for RemoteDataSource {
    fn login(&self, _c: &Credentials, _hwid: &str) -> Result<Session, DataError> {
        Err(DataError::Offline) // TODO(M5): POST {base}/v1/auth/login
    }
    fn catalog(&self) -> Result<Vec<SupportedApp>, DataError> {
        let _ = &self.base_url;
        Err(DataError::Offline)
    }
    fn entitlement(&self, _pkg: &str) -> Result<Entitlement, DataError> {
        Err(DataError::Offline)
    }
    fn lib_manifest(&self, _pkg: &str) -> Result<LibManifest, DataError> {
        Err(DataError::Offline)
    }
    fn download_lib(&self, _m: &LibManifest) -> Result<EncryptedBlob, DataError> {
        Err(DataError::Offline)
    }
    fn session_key(&self, _pkg: &str, _v: &str) -> Result<SessionKey, DataError> {
        Err(DataError::Offline)
    }
}
