package dev.lumora.launcher.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceCodeDialogTest {
    @Test
    void onlyMicrosoftHttpsPagesAreOpenedAutomatically() {
        assertTrue(DeviceCodeDialog.isTrusted("https://microsoft.com/link"));
        assertTrue(DeviceCodeDialog.isTrusted("https://www.microsoft.com/link"));
        assertTrue(DeviceCodeDialog.isTrusted("https://login.microsoftonline.com/common/oauth2/deviceauth"));
        assertFalse(DeviceCodeDialog.isTrusted("http://microsoft.com/link"));
        assertFalse(DeviceCodeDialog.isTrusted("https://microsoft.com.evil.example/link"));
        assertFalse(DeviceCodeDialog.isTrusted("https://evilmicrosoft.com/link"));
        assertFalse(DeviceCodeDialog.isTrusted("file:///C:/Windows/System32/calc.exe"));
        assertFalse(DeviceCodeDialog.isTrusted("not a uri"));
    }
}
