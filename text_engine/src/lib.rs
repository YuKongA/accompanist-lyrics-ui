#[macro_use]
extern crate log;

mod atlas;
mod core;
mod font;
mod jvm;
mod native;

/// Initialize logger - call this early from JNI init
#[cfg(target_os = "android")]
pub fn init_logging() {
    android_log::init("FontTower").ok();
}

#[cfg(not(target_os = "android"))]
pub fn init_logging() {
    // No-op on non-Android platforms, eprintln works fine
}
