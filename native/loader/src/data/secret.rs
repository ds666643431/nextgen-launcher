//! Minimal zeroize-on-drop secret wrappers with redacted Debug. Kept dependency-free
//! (no `zeroize` crate) so the fake build stays offline-buildable; §7/§8 may swap in
//! the `zeroize` crate for hardened wiping if warranted.

/// Bytes that are volatile-wiped on drop and never printed by Debug.
#[derive(Clone)]
pub struct SecretBytes(Vec<u8>);

impl SecretBytes {
    pub fn new(v: Vec<u8>) -> Self {
        Self(v)
    }
    pub fn expose(&self) -> &[u8] {
        &self.0
    }
    pub fn len(&self) -> usize {
        self.0.len()
    }
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl Drop for SecretBytes {
    fn drop(&mut self) {
        for b in self.0.iter_mut() {
            // volatile so the wipe is not optimized away
            unsafe { std::ptr::write_volatile(b, 0) };
        }
    }
}

impl std::fmt::Debug for SecretBytes {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "SecretBytes(**{} bytes**)", self.0.len())
    }
}

/// UTF-8 secret string with the same protections.
#[derive(Clone)]
pub struct SecretString(SecretBytes);

impl SecretString {
    pub fn new(s: String) -> Self {
        Self(SecretBytes::new(s.into_bytes()))
    }
    pub fn expose(&self) -> &str {
        std::str::from_utf8(self.0.expose()).unwrap_or("")
    }
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl std::fmt::Debug for SecretString {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "SecretString(**redacted**)")
    }
}
