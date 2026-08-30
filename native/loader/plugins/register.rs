//! THE plugin activation point — the ONE place plugins are turned on. Edit this to
//! choose which plugins run. Each plugin's register() may hook BOTH app-lifecycle
//! events (on_event) AND UI rendering (on_render); a plugin can do either or both.
//!
//! Every plugin is equal and independent — there is no built-in "free"/"premium"
//! tier; that is your distribution choice (keep a plugin's source private). Comment
//! a line out to drop that plugin.
//!
//! Pulled into the crate via a #[path] module declaration in src/plugin/mod.rs, so
//! `super` here is the `plugin` module (which owns the sibling plugin modules).

use super::Registry;

pub fn register_plugins(reg: &mut Registry) {

}
