package de.gultsch.common;

import java.util.regex.Pattern;

public class Patterns {

    public static final Pattern URI_GENERIC =
            Pattern.compile(
                    "(?<=^|\\p{Z}|\\s|\\p{P}|<)(tel|xmpp|http|https|geo|mailto|web\\+ap|gemini|bitcoin|bitcoincash|ethereum|monero|wownero):[\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]+(\\([\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]+\\))*[\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]*");

    public static final Pattern URI_TEL =
            Pattern.compile("^tel:\\+?(\\d{1,4}[-./()\\s]?)*\\d{1,4}(;.*)?$");

    public static final Pattern URI_HTTP = Pattern.compile("https?://\\S+");

    public static final Pattern URI_WEB_AP = Pattern.compile("web\\+ap://\\S+");

    public static Pattern URI_GEO =
            Pattern.compile(
                    "geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,-?\\d+(?:\\.\\d+)?)?(?:;crs=[\\w-]+)?(?:;u=\\d+(?:\\.\\d+)?)?(?:;[\\w-]+=(?:[\\w-_.!~*'()]|%[\\da-f][\\da-f])+)*(\\?z=\\d+)?",
                    Pattern.CASE_INSENSITIVE);

    public static final Pattern IPV4 =
            Pattern.compile(
                    "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6 =
            Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");
    public static final Pattern IPV6_HEX4_DECOMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)"
                        + " ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6_6HEX4DEC =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6_HEX_COMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");

    public static final Pattern BITCOIN_URI = Pattern
            .compile("bitcoin\\:(?:[13][a-km-zA-HJ-NP-Z1-9]{25,34}|[bB][cC]1[pPqQ][a-zA-Z0-9]{38,58})(?:\\?(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?");

    public static final Pattern BITCOINCASH_URI = Pattern
            .compile("bitcoincash\\:(?:[13][a-km-zA-HJ-NP-Z1-9]{33}|[qp][a-z0-9]{41})(?:\\?(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?");

    public static final Pattern ETHEREUM_URI = Pattern
            .compile("ethereum\\:(?:pay\\-)?(0x[0-9a-f]{40})(?:@[0-9]+)?(?:/(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?(?:\\?(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?");

    public static final Pattern MONERO_URI = Pattern
            .compile("monero\\:(?:[48][0-9AB][1-9A-HJ-NP-Za-km-z]{93})(?:\\?(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?");

    public static final Pattern WOWNERO_URI = Pattern
            .compile("wownero\\:(?:W(?:[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{96}|[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{106}|[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{187}))(?:\\?(?:(?:["
                    + Patterns.GOOD_IRI_CHAR
                    + "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
                    + "|(?:\\%[a-fA-F0-9]{2}))+)?");

    /**
     * Kept for backward compatibility reasons.
     *
     * @deprecated Deprecated since it does not include all IRI characters defined in RFC 3987
     */
    @Deprecated
    public static final String GOOD_IRI_CHAR =
            "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";

    private Patterns() {
        throw new AssertionError("Do not instantiate me");
    }
}
