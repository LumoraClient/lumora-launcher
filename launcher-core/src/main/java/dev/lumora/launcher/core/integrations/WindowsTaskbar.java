package dev.lumora.launcher.core.integrations;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import dev.lumora.launcher.core.util.Log;

import java.awt.Window;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * What Windows remembers when the user pins the launcher's taskbar button. Without this a pin points to the running
 * program, i.e. the extracted {@code portable\<hash>\LumoraLauncher\LumoraLauncher.exe}: such a pin starts the
 * launcher without the portable starter (no self-update) and breaks once an update removes that folder.
 * <p>
 * The documented window properties (SHGetPropertyStoreForWindow, Windows 7+) {@code PKEY_AppUserModel_ID},
 * {@code RelaunchCommand}, {@code RelaunchIconResource} and {@code RelaunchDisplayNameResource} make a pin start
 * {@code LumoraClient.exe} instead. They only live on the window (nothing in the registry) and are cleared again
 * before the window goes away, as the API asks. Every failure is logged and ignored: the launcher works without it.
 */
public final class WindowsTaskbar {
    /** Taskbar group of the launcher window (and of pins made from it). */
    public static final String APP_ID = "LumoraClient.Launcher";

    private static final String FMTID_APP_USER_MODEL = "9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3";
    private static final int PID_RELAUNCH_COMMAND = 2;
    private static final int PID_RELAUNCH_ICON = 3;
    private static final int PID_RELAUNCH_NAME = 4;
    private static final int PID_ID = 5;
    private static final String IID_IPROPERTYSTORE = "886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99";
    private static final short VT_EMPTY = 0;
    private static final short VT_LPWSTR = 31;
    /** sizeof(PROPVARIANT) on 64 bit (16 on 32 bit); the larger size is safe for both. */
    private static final int PROPVARIANT_SIZE = 24;
    // IPropertyStore vtable: QueryInterface, AddRef, Release, GetCount, GetAt, GetValue, SetValue, Commit
    private static final int RELEASE = 2;
    private static final int GET_VALUE = 5;
    private static final int SET_VALUE = 6;
    private static final int COMMIT = 7;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    private WindowsTaskbar() {
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The command line a pin of this window starts: {@code "<exe>"}. */
    static String relaunchCommand(Path exe) {
        return "\"" + exe.toAbsolutePath() + "\"";
    }

    /** Pins of {@code window} start {@code exe} (with its icon and {@code name}). Swing thread, window displayable. */
    public static boolean pinAs(Window window, Path exe, String name) {
        String exePath = exe.toAbsolutePath().toString();
        return withStore(window, store -> set(store, PID_RELAUNCH_COMMAND, relaunchCommand(exe))
                && set(store, PID_RELAUNCH_ICON, exePath + ",0")
                && set(store, PID_RELAUNCH_NAME, name)
                && set(store, PID_ID, APP_ID)
                && call(store, COMMIT) >= 0, "set");
    }

    /** Removes the properties again (before the window is disposed). */
    public static boolean clear(Window window) {
        return withStore(window, store -> set(store, PID_ID, null)
                && set(store, PID_RELAUNCH_COMMAND, null)
                && set(store, PID_RELAUNCH_ICON, null)
                && set(store, PID_RELAUNCH_NAME, null)
                && call(store, COMMIT) >= 0, "clear");
    }

    /** The relaunch command currently set on the window (diagnostics and tests). */
    public static Optional<String> currentRelaunchCommand(Window window) {
        String[] out = new String[1];
        withStore(window, store -> {
            out[0] = get(store, PID_RELAUNCH_COMMAND);
            return true;
        }, "read");
        return Optional.ofNullable(out[0]);
    }

    private interface StoreAction {
        boolean run(Pointer store);
    }

    private static boolean withStore(Window window, StoreAction action, String what) {
        if (!supported() || window == null || !window.isDisplayable()) {
            return false;
        }
        boolean uninit = false;
        Pointer store = null;
        try {
            int init = Function.getFunction("ole32", "CoInitializeEx", Function.ALT_CONVENTION)
                    .invokeInt(new Object[]{null, 2 /* COINIT_APARTMENTTHREADED */});
            uninit = init >= 0; // S_OK / S_FALSE must be balanced; RPC_E_CHANGED_MODE: COM is already set up differently
            if (init < 0 && init != RPC_E_CHANGED_MODE) {
                Log.info("Taskbar " + what + ": CoInitializeEx 0x" + Integer.toHexString(init));
            }
            Pointer hwnd = Native.getWindowPointer(window);
            if (hwnd == null) {
                return false;
            }
            PointerByReference ref = new PointerByReference();
            int hr = Function.getFunction("shell32", "SHGetPropertyStoreForWindow", Function.ALT_CONVENTION)
                    .invokeInt(new Object[]{hwnd, guid(IID_IPROPERTYSTORE), ref});
            if (hr < 0 || ref.getValue() == null) {
                Log.info("Taskbar " + what + ": SHGetPropertyStoreForWindow 0x" + Integer.toHexString(hr));
                return false;
            }
            store = ref.getValue();
            boolean ok = action.run(store);
            if (!ok) {
                Log.info("Taskbar " + what + ": a property could not be written");
            }
            return ok;
        } catch (Throwable e) { // UnsatisfiedLinkError, LinkageError, JNA errors: the launcher works without this
            Log.info("Taskbar " + what + " not available: " + e);
            return false;
        } finally {
            if (store != null) {
                try {
                    call(store, RELEASE);
                } catch (Throwable ignored) {
                    // nothing left to do
                }
            }
            if (uninit) {
                try {
                    Function.getFunction("ole32", "CoUninitialize", Function.ALT_CONVENTION).invokeVoid(new Object[0]);
                } catch (Throwable ignored) {
                    // nothing left to do
                }
            }
        }
    }

    /** Writes one string property ({@code null} clears it with VT_EMPTY). */
    private static boolean set(Pointer store, int pid, String value) {
        Memory pv = new Memory(PROPVARIANT_SIZE);
        pv.clear();
        Memory text = null;
        if (value != null) {
            text = new Memory((value.length() + 1L) * 2);
            text.setWideString(0, value);
            pv.setShort(0, VT_LPWSTR);
            pv.setPointer(8, text);
        } else {
            pv.setShort(0, VT_EMPTY);
        }
        int hr = method(store, SET_VALUE).invokeInt(new Object[]{store, key(pid), pv});
        // SetValue copies the value; keep the buffer reachable until here
        if (text != null) {
            text.size();
        }
        return hr >= 0;
    }

    private static String get(Pointer store, int pid) {
        Memory pv = new Memory(PROPVARIANT_SIZE);
        pv.clear();
        int hr = method(store, GET_VALUE).invokeInt(new Object[]{store, key(pid), pv});
        if (hr < 0) {
            return null;
        }
        try {
            if (pv.getShort(0) != VT_LPWSTR || pv.getPointer(8) == null) {
                return null;
            }
            return pv.getPointer(8).getWideString(0);
        } finally {
            Function.getFunction("ole32", "PropVariantClear", Function.ALT_CONVENTION).invokeInt(new Object[]{pv});
        }
    }

    private static int call(Pointer store, int index) {
        return method(store, index).invokeInt(new Object[]{store});
    }

    private static Function method(Pointer comObject, int index) {
        Pointer vtable = comObject.getPointer(0);
        return Function.getFunction(vtable.getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION);
    }

    /** PROPERTYKEY {fmtid, pid} of the AppUserModel property set. */
    private static Memory key(int pid) {
        Memory m = new Memory(20);
        m.write(0, guidBytes(FMTID_APP_USER_MODEL), 0, 16);
        m.setInt(16, pid);
        return m;
    }

    private static Memory guid(String text) {
        Memory m = new Memory(16);
        m.write(0, guidBytes(text), 0, 16);
        return m;
    }

    /** GUID in memory layout: Data1 (4), Data2 (2), Data3 (2) little endian, then Data4 (8) as written. */
    static byte[] guidBytes(String text) {
        String hex = text.replace("-", "");
        if (!hex.matches("[0-9A-Fa-f]{32}")) {
            throw new IllegalArgumentException(text);
        }
        byte[] raw = new byte[16];
        for (int i = 0; i < 16; i++) {
            raw[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new byte[]{raw[3], raw[2], raw[1], raw[0], raw[5], raw[4], raw[7], raw[6],
                raw[8], raw[9], raw[10], raw[11], raw[12], raw[13], raw[14], raw[15]};
    }
}
