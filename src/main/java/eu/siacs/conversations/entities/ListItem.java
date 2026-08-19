package eu.siacs.conversations.entities;

import android.content.Context;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;
import java.util.Collection;
import java.util.Locale;

public interface ListItem extends Comparable<ListItem>, AvatarService.Avatar {
    String getDisplayName();

    Jid getAddress();

    Collection<Tag> getTags();

    Account getAccount();

    default boolean match(final String needle) {
        if (Strings.isNullOrEmpty(needle)) {
            return true;
        }
        final var parts =
                Splitter.on(CharMatcher.whitespace())
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(needle.toLowerCase(Locale.ROOT));
        if (parts.size() == 1) {
            return matchInItem(Iterables.getOnlyElement(parts));
        } else {
            for (final var part : parts) {
                if (!matchInItem(part)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    default int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    private boolean matchInItem(final String needle) {
        return getAddress().toString().contains(needle)
                || getDisplayName().toLowerCase(Locale.US).contains(needle)
                || matchInTag(needle);
    }

    private boolean matchInTag(final String needle) {
        for (final Tag tag : getTags()) {
            if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    final class Tag implements Comparable {
        private final String name;

        public Tag(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public String toString() {
            return getName();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Tag)) return false;
            Tag ot = (Tag) o;
            return name.toLowerCase(Locale.US).equals(ot.getName().toLowerCase(Locale.US));
        }

        public int compareTo(Object o) {
            if (!(o instanceof Tag)) return -1;
            Tag ot = (Tag) o;
            return name.toLowerCase(Locale.US).compareTo(ot.getName().toLowerCase(Locale.US));
        }

        public int hashCode() {
            return name.toLowerCase(Locale.US).hashCode();
        }

        public static Collection<Tag> of(final Collection<String> tags) {
            return Collections2.transform(tags, Tag::new);
        }
    }
}
