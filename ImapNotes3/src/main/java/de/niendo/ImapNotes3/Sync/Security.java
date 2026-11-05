/*
 * Copyright (C) 2022-2023 - Peter Korf <peter@niendo.de>

 * Copyright (C)         ? - kwhitefoot
 * Copyright (C)      2015 - kj
 * and Contributors.
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.niendo.ImapNotes3.Sync;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Use this instead of integers in the account configuration.  Store the name of the security type instead.
 */

public enum Security {
    None("None"),
    SSL_TLS("SSL/TLS"),
    SSL_TLS_accept_all_certificates("SSL/TLS (accept all certificates)"),
    STARTTLS("STARTTLS"),
    STARTTLS_accept_all_certificates("STARTTLS (accept all certificates)");

    private final String printable;

    Security(String printable) {
        this.printable = printable;
    }

    public static List<String> Printables() {
        List<String> list = new ArrayList<String>();
        for (Security e : Security.values()) {
            list.add(e.printable);
        }
        return list;
    }
}
