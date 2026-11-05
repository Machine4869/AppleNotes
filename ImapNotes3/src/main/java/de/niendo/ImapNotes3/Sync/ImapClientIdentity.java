/*
 * Minimal RFC 2971 IMAP ID support for servers that require it (e.g. 163/NetEase).
 * This file is part of ImapNotes3 and is licensed under GPL-3.0.
 */
package de.niendo.ImapNotes3.Sync;

import com.sun.mail.imap.IMAPStore;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.mail.MessagingException;

/** Sends the RFC 2971 ID command after login, when the server advertises it. */
public final class ImapClientIdentity {
    private ImapClientIdentity() {
    }

    public static void send(IMAPStore store, String version) throws MessagingException {
        if (!store.hasCapability("ID")) {
            return;
        }
        Map<String, String> identity = new LinkedHashMap<>();
        identity.put("name", "ImapNotes3");
        identity.put("version", version);
        identity.put("vendor", "ImapNotes3 contributors");
        identity.put("support-url", "https://github.com/niendo1/ImapNotes3");
        store.id(identity);
    }
}
