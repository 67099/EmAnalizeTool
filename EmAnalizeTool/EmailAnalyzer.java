import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.SharedFileInputStream;

// Same extraction idea as EmTool, packaged so the GUI can call it.
// EmTool.java itself is left as the original command-line version.
public class EmailAnalyzer {

    public static class Report {
        public final File file;
        public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        public final List<String> links = new ArrayList<>();      // defanged
        public final List<String> attachments = new ArrayList<>();
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
            sb.append("\n--- links (Defanged) ---\n");
            if (links.isEmpty()) sb.append("No URLS found\n");
            for (String l : links) sb.append(l).append('\n');
            sb.append("\n--- attachments ---\n");
            if (attachments.isEmpty()) sb.append("none\n");
            for (String a : attachments) sb.append(a).append('\n');
            return sb.toString();
        }
    }

    public static Report analyze(File emlFile) throws Exception {
        Report r = new Report(emlFile);
        r.sha256 = sha256(emlFile);

        Session session = Session.getDefaultInstance(new Properties());
        try (SharedFileInputStream fis = new SharedFileInputStream(emlFile)) {
            MimeMessage msg = new MimeMessage(session, fis);

            r.headers.put("Subject", orDash(msg.getSubject()));
            r.headers.put("From", join(msg.getFrom()));
            r.headers.put("To", join(msg.getRecipients(Message.RecipientType.TO)));
            r.headers.put("Date", orDash(msg.getHeader("Date", null)));
            r.headers.put("Return-Path", orDash(msg.getHeader("Return-Path", null)));
            r.headers.put("Reply-To", join(msg.getReplyTo()));
            r.headers.put("Message-ID", orDash(msg.getHeader("Message-ID", null)));
            r.headers.put("SPF", orDash(msg.getHeader("Received-SPF", null)));

            StringBuilder body = new StringBuilder();
            collect(msg, body, r.attachments);

            Matcher m = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE).matcher(body);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            while (m.find()) seen.add(defangLink(m.group()));
            r.links.addAll(seen);
        }
        return r;
    }

    // walks the MIME tree: text/plain and text/html go to the body, named parts are attachments
    private static void collect(Part part, StringBuilder body, List<String> attachments) throws Exception {
        String name = part.getFileName();
        if (name != null && !part.isMimeType("multipart/*")) {
            attachments.add(MimeUtility.decodeText(name) + "  (" + baseType(part.getContentType()) + ")");
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

    private static String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.replaceAll("\\s+", " ").trim();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
