package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.util.Log;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drag and drop: files from the Windows Explorer and instance cards dragged inside Lumora. A target reacts only to what
 * it accepts and shows it while something is over it ({@code hover}); every drag over any target also reports
 * {@code active}, so the page can show its drop zones while a drag is going on.
 */
final class DropSupport {
    /** Prefix of an instance dragged inside the launcher (the rest is its selection key). */
    static final String INSTANCE = "lumora-instance:";

    /** What a target accepts and what it does with it. */
    record Handlers(Consumer<List<Path>> files, Consumer<String> instance, Consumer<Boolean> hover, Runnable active) {
    }

    private DropSupport() {
    }

    /** Lets an instance card be dragged (onto another instance or the "copy" zone). */
    static void instanceSource(Component c, Instance i) {
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(c, DnDConstants.ACTION_COPY,
                e -> e.startDrag(DragSource.DefaultCopyDrop, new StringSelection(INSTANCE + i.key())));
    }

    static void target(Component c, Handlers h) {
        new DropTarget(c, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            private boolean accepts(Transferable t) {
                return h.files() != null && t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || h.instance() != null && instanceKey(t) != null;
            }

            @Override
            public void dragEnter(DropTargetDragEvent e) {
                if (h.active() != null) {
                    h.active().run();
                }
                if (accepts(e.getTransferable())) {
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                    if (h.hover() != null) {
                        h.hover().accept(true);
                    }
                } else {
                    e.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent e) {
                if (h.active() != null) {
                    h.active().run();
                }
                if (accepts(e.getTransferable())) {
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    e.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent e) {
                if (h.hover() != null) {
                    h.hover().accept(false);
                }
            }

            @Override
            public void drop(DropTargetDropEvent e) {
                if (h.hover() != null) {
                    h.hover().accept(false);
                }
                Transferable t = e.getTransferable();
                try {
                    if (h.instance() != null && instanceKey(t) != null) {
                        e.acceptDrop(DnDConstants.ACTION_COPY);
                        h.instance().accept(instanceKey(t));
                        e.dropComplete(true);
                    } else if (h.files() != null && t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        e.acceptDrop(DnDConstants.ACTION_COPY);
                        List<Path> paths = new ArrayList<>();
                        for (Object o : (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor)) {
                            if (o instanceof File f) {
                                paths.add(f.toPath());
                            }
                        }
                        e.dropComplete(true);
                        h.files().accept(paths);
                    } else {
                        e.rejectDrop();
                    }
                } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException | RuntimeException ex) {
                    Log.warn("Drop failed: " + ex.getMessage());
                    e.dropComplete(false);
                }
            }
        }, true);
    }

    /** The selection key of an instance dragged inside the launcher, or null. */
    static String instanceKey(Transferable t) {
        if (!t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return null;
        }
        try {
            Object s = t.getTransferData(DataFlavor.stringFlavor);
            return s instanceof String str && str.startsWith(INSTANCE) ? str.substring(INSTANCE.length()) : null;
        } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
            return null;
        }
    }
}
