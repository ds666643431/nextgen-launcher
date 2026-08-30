//! LocalFakeDataSource — the DEFAULT backend. Serves a seeded catalog (the exact 6 apps
//! from the design), so the whole UI runs on-device/desktop with no server. The
//! launch-pipeline methods (§8) return fillers.

use std::sync::Mutex;
use std::time::Duration;

use super::models::*;
use super::secret::SecretBytes;
use super::source::{DataError, DataSource};

pub struct LocalFakeDataSource {
    session: Mutex<Option<Session>>,
}

impl LocalFakeDataSource {
    pub fn seeded() -> Self {
        Self {
            session: Mutex::new(None),
        }
    }

    pub fn catalog_seed() -> Vec<SupportedApp> {
        use Tier::*;
        let a = |pkg: &str, title: &str, tier: Tier, icon: &str, desc: &str| SupportedApp {
            pkg: pkg.into(),
            title: title.into(),
            tier,
            icon_url: icon.into(),
            description: desc.into(),
        };
        vec![
            a("com.kingdom.gwanyuIdle", "Guan Yu Idle", Plus, "https://play-lh.googleusercontent.com/tWai-WMATFb3wU9cCIudyRivoxvIlNPoW1k_9aYbpfNl5w6CFKkkCObjVqfrkgE4Ub0ha6KuL1BQlvfHvgDz=s120-rw",
              "God mode, unlimited gems and gold, and x10 idle rewards. Toggle each cheat from the in-game mod menu without a restart."),
            a("com.mouseduck.seawar", "Idle Poseidon", Plus, "https://play-lh.googleusercontent.com/GlDw8Ug1YXYzQvI2GXMbA7jdrWOL0AZAnoh4o44PM6VHdJaC5yC2eTnOJgHuU8dUIiFMezZGwh3RTTtH8Xysdy4=s120-rw",
              "Unlimited pearls, one-hit kill, and auto-battle at max speed. All options are switchable from the floating menu."),
            a("com.ig.weapon.backpack", "Backpack Hero", Vip, "https://play-lh.googleusercontent.com/ibY3el9T0d5h2gRKpJO2suBaCiUc_WeW7YPAN6W7r6GPs32sMmPE8qHTZ_EENAnlZtZp3CNfoPGHzMUHb534=s120-rw",
              "Infinite gold, free crafting, and all backpack slots unlocked. Includes a damage multiplier and god mode."),
            a("com.superpixel.pixelknights", "Pixel Knights", Plus, "https://play-lh.googleusercontent.com/jfU4u1kELp0k7LYtaRKh4vqmgFJ6r4FhRgUKkS-23PFsNkLC78eYXtLU0bY4hG2pDMh194gkPgmHoezpSk6a=s120-rw",
              "Damage multiplier, god mode, and unlimited energy so you never wait between runs."),
            a("com.daerigame.rellion", "RELLION", Vip, "https://play-lh.googleusercontent.com/92xAwNIv7rMAmfvwTbAHKV3ZmP5MKMrIKjNC-NQHZM6She2mKJ3XTeqo-QJ42Ov93ses-TSUvid2cPmJ1CGO=s120-rw",
              "Unlimited diamonds, no skill cooldowns, and instant upgrades. Full menu toggles for every feature."),
            a("com.returnpixel.pixelsummoners", "Pixel TamerZ", Plus, "https://play-lh.googleusercontent.com/PzDln9srgjngvBbJ4wfI99wUOwLEFSFFNfA7de-KRXIHjeaprkLcNxAAB-6vhhUCMpMU5mKGv-aNWjJxOElj=s120-rw",
              "Free summons, max stats, and unlimited in-game currency, all controllable from the mod menu."),
            a("com.ryonggame.soulhunteridle", "Soul Hunter Idle: AFK RPG", Plus, "https://play-lh.googleusercontent.com/PTFRuu4EyoYWZ5PNkJD3Z_SUxnByiFYYwFky5xxUzchwfuUfDs7hBveh7ru10kzhZrEGsebUvpGzSB1F5s2T1Xc=w120-rw",
              "God mode, x100 damage, and unlimited AFK rewards. Farm bosses hands-free with the menu enabled."),
            a("gr.nikolasspyr.integritycheck", "Play Integrity API Checker", Plus, "https://play-lh.googleusercontent.com/-jYM5HGgSyIVfQAmMqGyViaLpk635H5r2HKvA48OOaE13VcKkCz_d6tuuiTvNQ1ndnmqkIn-0Gljsz96xi2aBA=w120-rw",
              "God mode, x100 damage, and unlimited AFK rewards. Farm bosses hands-free with the menu enabled."),
        ]
    }
}

impl DataSource for LocalFakeDataSource {
    fn login(&self, creds: &Credentials, _hwid: &str) -> Result<Session, DataError> {
        if creds.license_key.is_empty() {
            return Err(DataError::Unauthorized);
        }
        let s = Session {
            subject: "kxbyte".into(),
            tier: "PLUS".into(),
            token: SecretBytes::new(b"fake-session-token".to_vec()),
            expires_unix: 4_102_444_800,
        };
        *self.session.lock().unwrap() = Some(s.clone());
        Ok(s)
    }

    fn catalog(&self) -> Result<Vec<SupportedApp>, DataError> {
        std::thread::sleep(Duration::from_millis(150)); // exercise the Loading state
        Ok(Self::catalog_seed())
    }

    fn entitlement(&self, pkg: &str) -> Result<Entitlement, DataError> {
        let granted = Self::catalog_seed().iter().any(|a| a.pkg == pkg);
        Ok(Entitlement {
            pkg: pkg.to_owned(),
            granted,
            plan: if granted { "pro" } else { "none" }.into(),
            expires_unix: granted.then_some(4_102_444_800),
        })
    }

    fn lib_manifest(&self, pkg: &str) -> Result<LibManifest, DataError> {
        if Self::catalog_seed().iter().all(|a| a.pkg != pkg) {
            return Err(DataError::NotFound);
        }
        Ok(LibManifest {
            game_pkg: pkg.to_owned(),
            build_id: 1,
            version: "1.0.0".into(),
            url: format!("fake://lib/{pkg}/1"),
            so_sha256: [0u8; 32],
            min_launcher: 1,
        })
    }

    fn download_lib(&self, manifest: &LibManifest) -> Result<EncryptedBlob, DataError> {
        Ok(EncryptedBlob {
            pkg: manifest.game_pkg.clone(),
            build_id: manifest.build_id,
            bytes: vec![0u8; 64],
            so_sha256: manifest.so_sha256,
        })
    }

    fn session_key(&self, pkg: &str, _version: &str) -> Result<SessionKey, DataError> {
        if self.session.lock().unwrap().is_none() {
            return Err(DataError::Unauthorized);
        }
        if !self.entitlement(pkg)?.granted {
            return Err(DataError::NotEntitled(pkg.to_owned()));
        }
        Ok(SessionKey {
            pkg: pkg.to_owned(),
            build_id: 1,
            transport_key: SecretBytes::new(vec![0x11; 32]),
            start_secret: SecretBytes::new(vec![0x22; 32]),
            expires_unix: 4_102_444_800,
        })
    }
}
