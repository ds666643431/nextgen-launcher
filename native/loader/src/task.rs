//! Background task runner. Blocking data/engine work runs here, never on the egui frame;
//! results come back as `Msg` and wake the UI via request_repaint. Single worker for now
//! (§2-6 work is light); a pool + download lane is a later refinement (§8).

use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::Arc;

use crate::data::models::SupportedApp;
use crate::data::DataSource;
use crate::engine::{Accounts, Engine};

pub enum Msg {
    CatalogLoaded(Result<Vec<SupportedApp>, String>),
    InstalledLoaded(Result<Vec<String>, String>),
    InstallableLoaded(Result<Vec<String>, String>),
    AddResult { pkg: String, res: Result<(), String> },
    LaunchResult { pkg: String, res: Result<(), String> },
    AccountsLoaded(Result<Accounts, String>),
    /// The engine server process is up + answering (pre-warm for a launch completed).
    CoreReady,
    Toast(String),
}

pub struct TaskCtx {
    pub data: Arc<dyn DataSource>,
    pub engine: Arc<dyn Engine>,
    pub out: Sender<Msg>,
    pub ctx: egui::Context,
}

impl TaskCtx {
    pub fn send(&self, m: Msg) {
        let _ = self.out.send(m);
        self.ctx.request_repaint();
    }
}

type Job = Box<dyn FnOnce(&TaskCtx) + Send + 'static>;

pub struct Tasks {
    job_tx: Sender<Job>,
    msg_rx: Receiver<Msg>,
}

impl Tasks {
    pub fn new(data: Arc<dyn DataSource>, engine: Arc<dyn Engine>, ctx: egui::Context) -> Self {
        let (job_tx, job_rx) = channel::<Job>();
        let (msg_tx, msg_rx) = channel::<Msg>();
        let cx = TaskCtx {
            data,
            engine,
            out: msg_tx,
            ctx,
        };
        std::thread::Builder::new()
            .name("ng-worker".into())
            .spawn(move || {
                while let Ok(job) = job_rx.recv() {
                    // Isolate a panicking job so it can't wedge every future task.
                    if std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| job(&cx))).is_err() {
                        log::error!("[ng-worker] a task panicked; worker continues");
                    }
                }
            })
            .ok();
        Tasks { job_tx, msg_rx }
    }

    pub fn spawn(&self, job: impl FnOnce(&TaskCtx) + Send + 'static) {
        let _ = self.job_tx.send(Box::new(job));
    }

    pub fn drain(&self) -> Vec<Msg> {
        self.msg_rx.try_iter().collect()
    }
}
