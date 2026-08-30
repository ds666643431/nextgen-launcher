//! Engine bridge (launcher-plan §5). The launcher drives the NextGen engine ONLY through
//! this trait. On Android it is ALWAYS the real engine (JNI → `VBridge` in the host app,
//! which bootstraps NextGenCore) — there is no fake engine. `StubEngine` exists only so the
//! crate still `cargo check`s on a desktop host (it is never used on device).

use std::sync::Arc;

#[cfg(target_os = "android")]
mod real;

#[derive(Clone, Debug)]
pub struct InstallOutcome {
    pub success: bool,
    pub package: String,
    pub msg: String,
}

#[derive(Debug, Clone)]
pub struct EngineError(pub String);

impl std::fmt::Display for EngineError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}
impl std::error::Error for EngineError {}

/// Linked-account state for the Settings sign-in section (VBridge account calls).
#[derive(Clone, Debug, Default)]
pub struct Accounts {
    /// The Google account email written into the container, if any.
    pub google: Option<String>,
    pub facebook: bool,
}

pub trait Engine: Send + Sync {
    /// Package names of apps cloned into the sandbox (the "Installed" set) — VBridge.listCloned.
    fn installed_pkgs(&self, user: i32) -> Result<Vec<String>, EngineError>;
    /// Package names of DEVICE apps that can be cloned (installed on the phone) —
    /// VBridge.listInstallable. Used to gate the details actions on device presence.
    fn installable_pkgs(&self) -> Result<Vec<String>, EngineError>;
    fn is_installed(&self, pkg: &str, user: i32) -> Result<bool, EngineError>;
    /// Clone a device-installed app into the sandbox — VBridge.install.
    fn install_from_device(&self, pkg: &str, user: i32) -> Result<InstallOutcome, EngineError>;
    /// Clone-if-needed then launch — VBridge.cloneAndLaunch.
    fn launch(&self, pkg: &str, user: i32) -> Result<bool, EngineError>;
    fn uninstall(&self, pkg: &str, user: i32) -> Result<(), EngineError>;

    // ---- accounts (Settings sign-in) — VBridge Google/Facebook calls ----
    /// Current linked-account state (blocking binder round-trips; worker-thread only).
    fn accounts(&self) -> Result<Accounts, EngineError>;
    /// Launch the Google EmbeddedSetup WebView flow — VBridge.startGoogleSignIn.
    fn start_google_sign_in(&self) -> Result<(), EngineError>;
    /// Launch the Facebook web-login flow — VBridge.startFacebookSignIn.
    fn start_facebook_sign_in(&self) -> Result<(), EngineError>;
    fn google_sign_out(&self) -> Result<(), EngineError>;
    fn facebook_sign_out(&self) -> Result<(), EngineError>;
}

pub fn build_engine() -> Arc<dyn Engine> {
    #[cfg(target_os = "android")]
    {
        Arc::new(real::RealEngine::new())
    }
    #[cfg(not(target_os = "android"))]
    {
        Arc::new(StubEngine)
    }
}

// Desktop-only compile shim (no JNI). Never used on device.
#[cfg(not(target_os = "android"))]
struct StubEngine;

#[cfg(not(target_os = "android"))]
impl Engine for StubEngine {
    fn installed_pkgs(&self, _user: i32) -> Result<Vec<String>, EngineError> {
        Ok(Vec::new())
    }
    fn installable_pkgs(&self) -> Result<Vec<String>, EngineError> {
        Ok(Vec::new())
    }
    fn is_installed(&self, _pkg: &str, _user: i32) -> Result<bool, EngineError> {
        Ok(false)
    }
    fn install_from_device(&self, _pkg: &str, _user: i32) -> Result<InstallOutcome, EngineError> {
        Err(EngineError("no engine on host".into()))
    }
    fn launch(&self, _pkg: &str, _user: i32) -> Result<bool, EngineError> {
        Err(EngineError("no engine on host".into()))
    }
    fn uninstall(&self, _pkg: &str, _user: i32) -> Result<(), EngineError> {
        Ok(())
    }
    fn accounts(&self) -> Result<Accounts, EngineError> {
        Ok(Accounts::default())
    }
    fn start_google_sign_in(&self) -> Result<(), EngineError> {
        Err(EngineError("no engine on host".into()))
    }
    fn start_facebook_sign_in(&self) -> Result<(), EngineError> {
        Err(EngineError("no engine on host".into()))
    }
    fn google_sign_out(&self) -> Result<(), EngineError> {
        Ok(())
    }
    fn facebook_sign_out(&self) -> Result<(), EngineError> {
        Ok(())
    }
}
