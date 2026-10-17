import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.SharedFileInputStream;

public class EmailAnalyzer {

    public static class Attachment {
        public final String name, type, sha256;
        public final long size;
        Attachment(String name, String type, long size, String sha256) {
            this.name = name; this.type = type; this.size = size; this.sha256 = sha256;
        }
        public String sizeText() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024));
        }
    }

    public static class Mismatch {
        public final String header, domain, fromDomain;
        Mismatch(String header, String domain, String fromDomain) {
            this.header = header; this.domain = domain; this.fromDomain = fromDomain;
        }
    }

    public static class Report {
        public final File file;
        public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        public final List<String> links = new ArrayList<>();
        public final List<Attachment> attachments = new ArrayList<>();
        public final List<String> ips = new ArrayList<>();
        public final List<Mismatch> mismatches = new ArrayList<>();
        public String sha256 = "";

        Report(File file) { this.file = file; }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("EmTool report\n");
            sb.append("file:    ").append(file.getAbsolutePath()).append('\n');
            sb.append("sha256:  ").append(sha256).append("\n\n");
            sb.append("--- Email Artifacts ---\n");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append("|  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            if (!mismatches.isEmpty()) {
                sb.append("\n--- warnings ---\n");
                for (Mismatch m : mismatches)
                    sb.append(m.header).append(" domain (").append(m.domain)
                      .append(") differs from From (").append(m.fromDomain).append(")\n");
            }
            sb.append("\n--- links (Defanged) ---\n");
            if (links.isEmpty()) sb.append("No URLS found\n");
            for (String l : links) sb.append(l).append('\n');
            sb.append("\n--- attachments ---\n");
            if (attachments.isEmpty()) sb.append("none\n");
            for (Attachment a : attachments)
                sb.append(a.name).append("  (").append(a.type).append(", ").append(a.sizeText()).append(")\n")
                  .append("   sha256 ").append(a.sha256).append('\n');
            sb.append("\n--- received IPs (first hop first) ---\n");
            if (ips.isEmpty()) sb.append("none\n");
            for (String ip : ips) sb.append(ip).append('\n');
            return sb.toString();
        }
    }

    static final Pattern IPV4 = Pattern.compile("(?<![\\d.])((?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3})(?![\\d.])");
    static final Pattern URL = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

    public static Report analyze(File emlFile) throws Exception {
        Report r = new Report(emlFile);
        r.sha256 = hex(digest(new FileInputStream(emlFile)));

        Session session = Session.getDefaultInstance(new Properties());
        try (SharedFileInputStream fis = new SharedFileInputStream(emlFile)) {
            MimeMessage msg = new MimeMessage(session, fis);

            String auth = orEmpty(msg.getHeader("Authentication-Results", "; "));
            String spf = msg.getHeader("Received-SPF", null);
            if (spf == null) spf = authResult(auth, "spf");

            r.headers.put("Subject", orDash(msg.getSubject()));
            r.headers.put("From", join(msg.getFrom()));
            r.headers.put("To", join(msg.getRecipients(Message.RecipientType.TO)));
            r.headers.put("Date", orDash(msg.getHeader("Date", null)));
            r.headers.put("Return-Path", orDash(msg.getHeader("Return-Path", null)));
            r.headers.put("Reply-To", msg.getHeader("Reply-To") == null ? "-" : join(msg.getReplyTo()));
            r.headers.put("Message-ID", orDash(msg.getHeader("Message-ID", null)));
            r.headers.put("SPF", orDash(spf));
            r.headers.put("DKIM", orDash(authResult(auth, "dkim")));
            r.headers.put("DMARC", orDash(authResult(auth, "dmarc")));
            r.headers.put("X-Mailer", orDash(msg.getHeader("X-Mailer", null)));

            checkDomains(r);

            String[] received = msg.getHeader("Received");
            if (received != null) {
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (int i = received.length - 1; i >= 0; i--) {
                    Matcher m = IPV4.matcher(received[i]);
                    while (m.find()) if (!m.group(1).startsWith("127.")) seen.add(m.group(1));
                }
                r.ips.addAll(seen);
            }

            StringBuilder body = new StringBuilder();
            collect(msg, body, r.attachments);

            Matcher m = URL.matcher(body);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            while (m.find()) seen.add(defangLink(m.group()));
            r.links.addAll(seen);
        }
        return r;
    }

    private static void collect(Part part, StringBuilder body, List<Attachment> attachments) throws Exception {
        String name = part.getFileName();
        if (name != null && !part.isMimeType("multipart/*")) {
            CountingStream in = new CountingStream(part.getInputStream());
            String hash = hex(digest(in));
            attachments.add(new Attachment(MimeUtility.decodeText(name), baseType(part.getContentType()), in.count, hash));
            return;
        }
        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            body.append(part.getContent().toString()).append('\n');
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) collect(mp.getBodyPart(i), body, attachments);
        } else if (part.isMimeType("message/rfc822")) {
            collect((Part) part.getContent(), body, attachments);
        }
    }

    private static void checkDomains(Report r) {
        String from = domainOf(r.headers.get("From"));
        if (from == null) return;
        for (String h : new String[]{"Return-Path", "Reply-To"}) {
            String d = domainOf(r.headers.get(h));
            if (d != null && !sameOrg(d, from)) r.mismatches.add(new Mismatch(h, d, from));
        }
    }

    private static boolean sameOrg(String a, String b) {
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private static String domainOf(String header) {
        if (header == null) return null;
        Matcher m = Pattern.compile("@([A-Za-z0-9.-]+)").matcher(header);
        return m.find() ? m.group(1).toLowerCase().replaceAll("\\.$", "") : null;
    }

    private static String authResult(String auth, String method) {
        Matcher m = Pattern.compile("\\b" + method + "=(\\w+)([^;]*)", Pattern.CASE_INSENSITIVE).matcher(auth);
        return m.find() ? (m.group(1) + m.group(2)).trim() : null;
    }

    static String defangLink(String url) {
        return url.replace("http", "hxxp").replace(".", "[.]");
    }

    private static String baseType(String ct) {
        if (ct == null) return "?";
        int i = ct.indexOf(';');
        return (i < 0 ? ct : ct.substring(0, i)).trim().toLowerCase();
    }

    private static String join(Address[] a) {
        if (a == null || a.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i] instanceof InternetAddress ? ((InternetAddress) a[i]).toUnicodeString() : a[i].toString());
        }
        return sb.toString();
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.replaceAll("\\s+", " ").trim();
    }

    private static byte[] digest(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream s = in) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) md.update(buf, 0, n);
        }
        return md.digest();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static class CountingStream extends FilterInputStream {
        long count;
        CountingStream(InputStream in) { super(in); }
        @Override public int read() throws IOException { int c = super.read(); if (c >= 0) count++; return c; }
        @Override public int read(byte[] b, int o, int l) throws IOException { int n = super.read(b, o, l); if (n > 0) count += n; return n; }
    }
}
