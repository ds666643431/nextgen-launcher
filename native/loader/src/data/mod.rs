//! Data adapter (launcher-plan §4). All data flows through the `DataSource` trait; the
//! fake↔remote choice is a single feature-gated factory (`build_data_source`) — no
//! call site ever names a concrete source.

pub mod local;
pub mod models;
pub mod secret;
pub mod source;

#[cfg(feature = "remote")]
pub mod remote;

pub use models::*;
pub use secret::{SecretBytes, SecretString};
pub use source::{DataError, DataSource};

use std::sync::Arc;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Backend {
    LocalFake,
    Remote,
}

impl Default for Backend {
    fn default() -> Self {
        if cfg!(feature = "remote") {
            Backend::Remote
        } else {
            Backend::LocalFake
        }
    }
}

#[derive(Clone, Debug)]
pub struct AppConfig {
    pub backend: Backend,
    pub base_url: String,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            backend: Backend::default(),
            base_url: obs!("https://api.nextgen.invalid").into(),
        }
    }
}

/// THE switch. Release: `cargo ndk -t arm64-v8a build --release --no-default-features
/// --features remote` flips `Backend::default()` → Remote AND compiles the remote impl.
/// No screen/pipeline code changes.
pub fn build_data_source(cfg: &AppConfig) -> Arc<dyn DataSource> {
    match cfg.backend {
        #[cfg(feature = "remote")]
        Backend::Remote => Arc::new(remote::RemoteDataSource::new(&cfg.base_url)),
        // Debug builds can't reach remote — fall back to fake.
        #[cfg(not(feature = "remote"))]
        Backend::Remote => Arc::new(local::LocalFakeDataSource::seeded()),
        Backend::LocalFake => Arc::new(local::LocalFakeDataSource::seeded()),
    }
}
