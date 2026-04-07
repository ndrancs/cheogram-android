/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.gultsch.common;

import android.net.Uri;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.Spannable;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Linkify {

    private static final android.text.util.Linkify.MatchFilter MATCH_FILTER =
            (s, start, end) -> isPassAdditionalValidation(s.subSequence(start, end).toString());

    private static boolean isPassAdditionalValidation(final String match) {
        final var scheme = Iterables.getFirst(Splitter.on(':').limit(2).splitToList(match), null);
        if (scheme == null) {
            return false;
        }
        if (!isValidUri(match)) {
            return false;
        }
        return switch (scheme) {
            case "tel" -> Patterns.URI_TEL.matcher(match).matches();
            case "http", "https" -> Patterns.URI_HTTP.matcher(match).matches();
            case "geo" -> Patterns.URI_GEO.matcher(match).matches();
            case "xmpp" -> new XmppUri(Uri.parse(match)).isValidJid();
            case "web+ap" -> {
                if (Patterns.URI_WEB_AP.matcher(match).matches()) {
                    final var webAp = new MiniUri(match);
                    // TODO once we have fragment support check that there aren't any
                    yield Objects.nonNull(webAp.getAuthority()) && webAp.getParameter().isEmpty();
                } else {
                    yield false;
                }
            }
            default -> true;
        };
    }

    private static boolean isValidUri(final String match) {
        try {
            new URI(match);
            return true;
        } catch (final URISyntaxException e) {
            return false;
        }
    }

    public static void addLinks(final Spannable body) {
        android.text.util.Linkify.addLinks(body, Patterns.URI_GENERIC, null, MATCH_FILTER, null);
        for (final URLSpan span : body.getSpans(0, body.length(), URLSpan.class)) {
            try {
                new java.net.URI(span.getURL());
            } catch (final java.net.URISyntaxException e) {
                body.removeSpan(span);
            }
        }
    }

    public static void addLinks(final Editable body, final Account account, final Jid context) {
        addLinks(body);
        final var roster = account.getRoster();
        urlspan:
        for (final URLSpan urlspan : body.getSpans(0, body.length() - 1, URLSpan.class)) {
            final var start = body.getSpanStart(urlspan);
            if (start < 0) continue;
            for (final var span : body.getSpans(start, start, Object.class))  {
                // instanceof TypefaceSpan is to block in XHTML code blocks. Probably a bit heavy-handed but works for now
                if ((body.getSpanFlags(span) & Spanned.SPAN_USER) >> Spanned.SPAN_USER_SHIFT == StylingHelper.NOLINKIFY || span instanceof TypefaceSpan) {
                    body.removeSpan(urlspan);
                    continue urlspan;
                }
            }
            Uri uri = Uri.parse(urlspan.getURL());
            if ("xmpp".equals(uri.getScheme())) {
                try {
                    if (!body.subSequence(body.getSpanStart(urlspan), body.getSpanEnd(urlspan)).toString().startsWith("xmpp:")) {
                        // Already customized
                        continue;
                    }

                    XmppUri xmppUri = new XmppUri(uri);
                    Jid jid = xmppUri.getJid();
                    String display = xmppUri.toString();
                    if (jid.asBareJid().equals(context) && xmppUri.isAction("message") && xmppUri.getBody() != null) {
                        display = xmppUri.getBody();
                    } else if (jid.asBareJid().equals(context) && xmppUri.parameterString().length() > 0) {
                        display = xmppUri.parameterString();
                    } else {
                        ListItem item = account.getBookmark(jid);
                        if (item == null) item = roster.getContact(jid);
                        display = item.getDisplayName() + xmppUri.displayParameterString();
                    }
                    body.replace(
                        body.getSpanStart(urlspan),
                        body.getSpanEnd(urlspan),
                        display
                    );
                } catch (final IllegalArgumentException | IndexOutOfBoundsException e) { /* bad JID or span gone */ }
            }
        }
    }

    public static List<MiniUri> getLinks(final String body) {
        final var builder = new ImmutableList.Builder<MiniUri>();
        final var matcher = Patterns.URI_GENERIC.matcher(body);
        while (matcher.find()) {
            final var match = matcher.group();
            if (isPassAdditionalValidation(match)) {
                builder.add(new MiniUri(match));
            }
        }
        return builder.build();
    }

	public static List<String> extractLinks(final Editable body) {
        addLinks(body);
        final var spans =
                Arrays.asList(body.getSpans(0, body.length() - 1, URLSpan.class));
        final var urlWrappers =
                Collections2.filter(
                        Collections2.transform(
                                spans,
                                s ->
                                        s == null
                                                ? null
                                                : new UrlWrapper(body.getSpanStart(s), s.getURL())),
                        uw -> uw != null);
        List<UrlWrapper> sorted = ImmutableList.sortedCopyOf(
                (a, b) -> Integer.compare(a.position, b.position), urlWrappers);
        return Lists.transform(sorted, uw -> uw.url);

    }

    private static class UrlWrapper {
        private final int position;
        private final String url;

        private UrlWrapper(int position, String url) {
            this.position = position;
            this.url = url;
        }
    }
}
