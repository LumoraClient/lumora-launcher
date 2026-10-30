package dev.lumora.launcher.core.install;

/** Install/launch progress, called from background threads. */
public interface ProgressListener {
    void stage(String description);

    /** @param fraction 0..1, or negative for "indeterminate" */
    void progress(double fraction, String detail);

    ProgressListener NONE = new ProgressListener() {
        @Override
        public void stage(String description) {
        }

        @Override
        public void progress(double fraction, String detail) {
        }
    };
}
