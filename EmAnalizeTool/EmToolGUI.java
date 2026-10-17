import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

public class EmToolGUI extends JFrame {

    // colors
    static boolean dark = true;
    static Color PAPER, SHEET, INK, MUTED, RULE, RUST, OLIVE, WARN_BG;
    static { palette(true); }

    static void palette(boolean d) {
        dark = d;
        if (d) {
            PAPER = new Color(0x141210); SHEET = new Color(0x1D1A17); INK = new Color(0xE8E1D3);
            MUTED = new Color(0x8A8174); RULE = new Color(0x332E28); RUST = new Color(0xE0673F);
            OLIVE = new Color(0xA3BE6E); WARN_BG = new Color(0x351C13);
        } else {
            PAPER = new Color(0xF1ECE2); SHEET = new Color(0xFAF7F1); INK = new Color(0x1D1B18);
            MUTED = new Color(0x7A7266); RULE = new Color(0xD6CEC0); RUST = new Color(0xA8361B);
            OLIVE = new Color(0x4F6B2E); WARN_BG = new Color(0xF4E1D8);
        }
    }

    static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    static final String UI_FAMILY = findUiFamily();

    static final Font MONO      = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 13);
    static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    static final Font MARK      = new Font(Font.MONOSPACED, Font.BOLD, 30);
    static final Font UI        = new Font(UI_FAMILY, Font.PLAIN, 14);
    static final Font UI_SMALL  = new Font(UI_FAMILY, Font.PLAIN, 12);
    static final Font UI_BOLD   = new Font(UI_FAMILY, Font.BOLD, 13);
    static final Font UI_TITLE  = new Font(UI_FAMILY, Font.BOLD, 14);

    // strings
    static final String[][] TEXT = {
        {"tagline",   "email artifact extractor",                "محلل الإيميلات"},
        {"drop",      "Drop a .eml file here, or",               "اسحب ملف الإيميل هنا، أو"},
        {"browse",    "browse",                                  "اختر ملف"},
        {"analyze",   "Analyze",                                 "حلّل"},
        {"headers",   "Email info",                              "معلومات الإيميل"},
        {"links",     "Links (defanged)",                        "الروابط (معطّلة)"},
        {"attach",    "Attachments",                             "المرفقات"},
        {"ips",       "Server IPs (sender first)",               "عناوين IP (من المرسل للمستلم)"},
        {"noattach",  "No attachments.",                         "ما فيه مرفقات."},
        {"noips",     "No IPs found.",                           "ما لقيت عناوين IP."},
        {"copy",      "Copy report",                             "نسخ التقرير"},
        {"save",      "Save .txt",                               "حفظ كملف نصي"},
        {"ready",     "ready. pick an .eml file to start",       "جاهز. اختر ملف الإيميل عشان نبدأ"},
        {"notfound",  "Error 404 |E!  file not found",           "Error 404 |E!  الملف مو موجود"},
        {"failed",    "could not read this file: ",              "ما قدرت أفتح الملف: "},
        {"done",      "done.",                                   "تم."},
        {"copied",    "copied",                                  "تم النسخ"},
        {"saved",     "saved to ",                               "انحفظ في "},
        {"empty",     "nothing here yet",                        "لسا ما اخترت ملف"},
        {"hint",      "double-click any line to copy it",        "دبل كلك على أي سطر عشان تنسخه"},
        {"private",   "internal network",                        "شبكة داخلية"},
        {"mismatch",  "! %s goes to %s, but the sender is %s",  "! %s يروح لـ %s، والمرسل %s"},
    };
    static final Map<String, String[]> T = new HashMap<>();
    static { for (String[] r : TEXT) T.put(r[0], new String[]{r[1], r[2]}); }

    static final Map<String, String> AR_KEYS = new HashMap<>();
    static {
        AR_KEYS.put("Subject", "الموضوع");
        AR_KEYS.put("From", "من");
        AR_KEYS.put("To", "إلى");
        AR_KEYS.put("Date", "التاريخ");
        AR_KEYS.put("Reply-To", "الرد يروح لـ");
    }
    static final String[] KEYS = {"Subject", "From", "To", "Date", "Return-Path", "Reply-To", "Message-ID", "SPF", "DKIM", "DMARC", "X-Mailer"};

    boolean arabic = false;
    String t(String k) { return T.get(k)[arabic ? 1 : 0]; }

    // state
    EmailAnalyzer.Report report;
    final JTextField pathField = new JTextField();
    final JLabel tagline = new JLabel();
    final JLabel dropLabel = new JLabel();
    final FlatButton browseBtn = new FlatButton(false);
    final FlatButton analyzeBtn = new FlatButton(true);
    final FlatButton copyBtn = new FlatButton(false);
    final FlatButton saveBtn = new FlatButton(false);
    final Toggle lang = new Toggle(() -> "EN", () -> "عربي", () -> arabic, () -> setArabic(!arabic));
    final Toggle theme = new Toggle(() -> arabic ? "داكن" : "DARK", () -> arabic ? "فاتح" : "LIGHT", () -> !dark, this::flipTheme);
    final SectionTitle hHead = new SectionTitle("01");
    final SectionTitle lHead = new SectionTitle("02");
    final SectionTitle aHead = new SectionTitle("03");
    final SectionTitle iHead = new SectionTitle("04");
    final JPanel warnBox = new JPanel();
    final JPanel headerRows = new JPanel(new GridBagLayout());
    JScrollPane headerScroll;
    final CopyList linkList = new CopyList(RUST);
    final CopyList attachList = new CopyList(INK);
    final CopyList ipList = new CopyList(INK);
    final JLabel status = new JLabel();
    final JLabel hashLabel = new JLabel();
    final JLabel hintLabel = new JLabel();
    String statusKey = "ready", statusExtra = "";
    boolean statusError = false;

    public EmToolGUI() {
        super("EmTool");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 640));
        setSize(1100, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAPER);
        root.setBorder(new EmptyBorder(22, 28, 14, 28));
        root.add(buildTop(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);

        root.setTransferHandler(new FileDrop());
        pathField.setTransferHandler(new FileDrop());

        refreshText();
        renderReport();
    }

    // layout

    JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel mark = new JLabel("EmTool");
        mark.setFont(MARK);
        mark.setForeground(INK);
        tagline.setForeground(MUTED);
        brand.add(mark);
        brand.add(tagline);
        top.add(brand, BorderLayout.LINE_START);

        JPanel langWrap = new JPanel(new FlowLayout(FlowLayout.TRAILING, 18, 6));
        langWrap.setOpaque(false);
        langWrap.add(theme);
        langWrap.add(lang);
        top.add(langWrap, BorderLayout.LINE_END);

        JPanel strip = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(SHEET);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(MUTED);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        strip.setOpaque(false);
        strip.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        left.setOpaque(false);
        dropLabel.setFont(UI);
        dropLabel.setForeground(INK);
        browseBtn.addActionListener(e -> browse());
        left.add(dropLabel);
        left.add(browseBtn);

        pathField.setFont(MONO);
        pathField.setForeground(INK);
        pathField.setBackground(SHEET);
        pathField.setCaretColor(RUST);
        pathField.setBorder(new MatteBorder(0, 0, 1, 0, INK));
        pathField.addActionListener(e -> analyze());
        pathField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        analyzeBtn.addActionListener(e -> analyze());

        strip.add(left, BorderLayout.LINE_START);
        strip.add(pathField, BorderLayout.CENTER);
        strip.add(analyzeBtn, BorderLayout.LINE_END);

        JPanel wrap = new JPanel(new BorderLayout(0, 18));
        wrap.setOpaque(false);
        wrap.add(top, BorderLayout.NORTH);
        wrap.add(strip, BorderLayout.CENTER);
        wrap.setBorder(new EmptyBorder(0, 0, 18, 0));
        return wrap;
    }

    JComponent buildBody() {
        headerRows.setBackground(SHEET);
        warnBox.setLayout(new BoxLayout(warnBox, BoxLayout.Y_AXIS));
        warnBox.setBackground(WARN_BG);
        JPanel hInner = new JPanel(new BorderLayout());
        hInner.setBackground(SHEET);
        hInner.add(warnBox, BorderLayout.NORTH);
        headerScroll = plainScroll(headerRows);
        hInner.add(headerScroll, BorderLayout.CENTER);

        JPanel ipCol = column(iHead, plainScroll(ipList));
        ipCol.setPreferredSize(new Dimension(100, 130));

        JPanel leftCol = new JPanel(new BorderLayout(0, 16));
        leftCol.setOpaque(false);
        leftCol.add(column(hHead, hInner), BorderLayout.CENTER);
        leftCol.add(ipCol, BorderLayout.SOUTH);

        JPanel lInner = new JPanel(new BorderLayout());
        lInner.setBackground(SHEET);
        lInner.add(plainScroll(linkList), BorderLayout.CENTER);
        hintLabel.setFont(UI_SMALL);
        hintLabel.setForeground(MUTED);
        hintLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        lInner.add(hintLabel, BorderLayout.SOUTH);

        JPanel attCol = column(aHead, plainScroll(attachList));
        attCol.setPreferredSize(new Dimension(100, 180));

        JPanel rightCol = new JPanel(new BorderLayout(0, 16));
        rightCol.setOpaque(false);
        rightCol.add(column(lHead, lInner), BorderLayout.CENTER);
        rightCol.add(attCol, BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 22, 0));
        body.setOpaque(false);
        body.add(leftCol);
        body.add(rightCol);
        return body;
    }

    JComponent buildFooter() {
        JPanel f = new JPanel(new BorderLayout(12, 0));
        f.setOpaque(false);
        f.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, RULE), new EmptyBorder(10, 0, 0, 0)));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        hashLabel.setFont(MONO_SMALL);
        hashLabel.setForeground(MUTED);
        info.add(status);
        info.add(hashLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        actions.setOpaque(false);
        copyBtn.addActionListener(e -> {
            if (report != null) { copy(report.toText()); setStatus("copied", "", false); }
        });
        saveBtn.addActionListener(e -> save());
        actions.add(copyBtn);
        actions.add(saveBtn);

        f.add(info, BorderLayout.CENTER);
        f.add(actions, BorderLayout.LINE_END);
        return f;
    }

    JPanel column(SectionTitle title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(title, BorderLayout.NORTH);
        JPanel sheet = new JPanel(new BorderLayout());
        sheet.setBackground(SHEET);
        sheet.setBorder(new LineBorder(RULE));
        sheet.add(content, BorderLayout.CENTER);
        p.add(sheet, BorderLayout.CENTER);
        return p;
    }

    static JScrollPane plainScroll(JComponent c) {
        JScrollPane s = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setBackground(SHEET);
        s.getVerticalScrollBar().setUnitIncrement(14);
        s.getVerticalScrollBar().setPreferredSize(new Dimension(8, 8));
        s.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = RULE; trackColor = SHEET; }
            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                g.setColor(isDragging ? MUTED : RULE);
                g.fillRect(r.x + 2, r.y, r.width - 4, r.height);
            }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
                g.setColor(SHEET);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
        return s;
    }

    // actions

    void browse() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Email (.eml)", "eml"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(fc.getSelectedFile().getAbsolutePath());
            analyze();
        }
    }

    void analyze() {
        String p = pathField.getText().trim();
        if (p.startsWith("\"") && p.endsWith("\"") && p.length() > 1) p = p.substring(1, p.length() - 1);
        File f = new File(p);
        if (p.isEmpty() || !f.isFile()) {
            setStatus("notfound", "", true);
            return;
        }
        try {
            report = EmailAnalyzer.analyze(f);
            setStatus("done", "  " + f.getName(), false);
        } catch (Exception ex) {
            report = null;
            setStatus("failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), true);
        }
        renderReport();
    }

    void save() {
        if (report == null) return;
        JFileChooser fc = new JFileChooser(report.file.getParentFile());
        String base = report.file.getName().replaceAll("\\.eml$", "");
        fc.setSelectedFile(new File(report.file.getParentFile(), base + "_report.txt"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(fc.getSelectedFile().toPath(), report.toText().getBytes(StandardCharsets.UTF_8));
                setStatus("saved", fc.getSelectedFile().getName(), false);
            } catch (IOException ex) {
                setStatus("failed", ex.getMessage(), true);
            }
        }
    }

    static void copy(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    void setStatus(String key, String extra, boolean error) {
        statusKey = key; statusExtra = extra; statusError = error;
        status.setText(t(key) + extra);
        status.setForeground(error ? RUST : INK);
    }

    void renderReport() {
        ComponentOrientation o = getContentPane().getComponentOrientation();

        headerRows.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        for (String k : KEYS) addRow(c, k, report == null ? "" : report.headers.get(k));
        c.gridy++; c.weighty = 1; c.gridx = 0;
        headerRows.add(Box.createGlue(), c);

        warnBox.removeAll();
        if (report != null) {
            for (EmailAnalyzer.Mismatch m : report.mismatches) {
                JTextArea w = new JTextArea(String.format(t("mismatch"), m.header, m.domain, m.fromDomain));
                w.setEditable(false);
                w.setLineWrap(true);
                w.setWrapStyleWord(true);
                w.setOpaque(false);
                w.setFont(UI_BOLD);
                w.setForeground(RUST);
                w.setBorder(new EmptyBorder(6, 12, 6, 12));
                warnBox.add(w);
            }
        }
        warnBox.setVisible(warnBox.getComponentCount() > 0);

        linkList.clear();
        attachList.clear();
        ipList.clear();
        if (report == null) {
            linkList.placeholder = attachList.placeholder = ipList.placeholder = t("empty");
        } else {
            linkList.placeholder = "-";
            attachList.placeholder = t("noattach");
            ipList.placeholder = t("noips");
            for (String l : report.links) linkList.add(l, null, l);
            for (EmailAnalyzer.Attachment a : report.attachments)
                attachList.add(a.name + "   " + a.type + " · " + a.sizeText(), a.sha256, a.sha256);
            for (String ip : report.ips) ipList.add(ip, isPrivate(ip) ? t("private") : null, ip);
        }

        lHead.count = report == null ? -1 : report.links.size();
        aHead.count = report == null ? -1 : report.attachments.size();
        iHead.count = report == null ? -1 : report.ips.size();
        hashLabel.setText(report == null ? " " : "sha256  " + report.sha256);
        copyBtn.setEnabled(report != null);
        saveBtn.setEnabled(report != null);

        headerRows.applyComponentOrientation(o);
        warnBox.applyComponentOrientation(o);
        for (Component c2 : headerRows.getComponents())
            if (c2 instanceof JTextArea) c2.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> headerScroll.getViewport().setViewPosition(new Point(0, 0)));
    }

    void addRow(GridBagConstraints c, String key, String value) {
        if (value == null) value = "";
        boolean ar = arabic && AR_KEYS.containsKey(key);
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(6, 12, 6, 10);
        JLabel k = new JLabel(ar ? AR_KEYS.get(key) : key.toUpperCase());
        k.setFont(ar ? UI_BOLD : new Font(Font.MONOSPACED, Font.BOLD, 11));
        k.setForeground(MUTED);
        k.setPreferredSize(new Dimension(96, 18));
        headerRows.add(k, c);

        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 12);
        JTextArea v = new JTextArea(value.isEmpty() ? "·" : value);
        v.setEditable(false);
        v.setLineWrap(true);
        v.setWrapStyleWord(false);
        v.setFont(MONO);
        v.setOpaque(false);
        v.setBorder(null);
        v.setSelectionColor(RUST);
        v.setSelectedTextColor(SHEET);
        ((javax.swing.text.DefaultCaret) v.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        v.setForeground(value.isEmpty() || value.equals("-") ? MUTED : INK);
        if ((key.equals("SPF") || key.equals("DKIM") || key.equals("DMARC")) && !value.isEmpty() && !value.equals("-")) {
            String low = value.toLowerCase();
            if (low.startsWith("pass")) v.setForeground(OLIVE);
            else if (low.startsWith("fail") || low.startsWith("softfail") || low.startsWith("none")) v.setForeground(RUST);
            v.setFont(MONO_BOLD);
        }
        headerRows.add(v, c);
        c.gridy++;

        c.gridx = 0; c.gridwidth = 2; c.insets = new Insets(0, 12, 0, 12);
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel line = new JPanel();
        line.setBackground(RULE);
        line.setPreferredSize(new Dimension(1, 1));
        headerRows.add(line, c);
        c.gridwidth = 1;
        c.gridy++;
    }

    void setArabic(boolean ar) {
        arabic = ar;
        ComponentOrientation o = ar ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT;
        getContentPane().applyComponentOrientation(o);
        pathField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        for (CopyList l : new CopyList[]{linkList, attachList, ipList})
            SwingUtilities.getAncestorOfClass(JScrollPane.class, l).applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        refreshText();
        renderReport();
    }

    void flipTheme() {
        palette(!dark);
        EmToolGUI w = new EmToolGUI();
        w.setBounds(getBounds());
        w.pathField.setText(pathField.getText());
        w.report = report;
        w.statusKey = statusKey; w.statusExtra = statusExtra; w.statusError = statusError;
        if (arabic) w.setArabic(true); else { w.refreshText(); w.renderReport(); }
        w.setVisible(true);
        dispose();
    }

    void refreshText() {
        tagline.setText(t("tagline"));
        dropLabel.setText(t("drop"));
        browseBtn.setText(t("browse"));
        analyzeBtn.setText(t("analyze"));
        copyBtn.setText(t("copy"));
        saveBtn.setText(t("save"));
        hHead.text = t("headers");
        lHead.text = t("links");
        aHead.text = t("attach");
        iHead.text = t("ips");
        hintLabel.setText(t("hint"));
        status.setText(t(statusKey) + statusExtra);
        status.setForeground(statusError ? RUST : INK);
        tagline.setFont(arabic ? UI : MONO);
        status.setFont(arabic ? UI : MONO);
        hHead.setFont(UI_TITLE); lHead.setFont(UI_TITLE); aHead.setFont(UI_TITLE); iHead.setFont(UI_TITLE);
        lang.repaint();
        theme.repaint();
        repaint();
    }

    // fonts

    static String findUiFamily() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (File dir : new File[]{new File("fonts"), new File(codeDir(), "fonts")}) {
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(ttf|otf)$"));
            if (files == null) continue;
            for (File f : files) {
                try { ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, f)); } catch (Exception ignored) { }
            }
        }
        String fallback = null;
        for (String fam : ge.getAvailableFontFamilyNames(Locale.ROOT)) {
            String low = fam.toLowerCase();
            if (!low.contains("thmanyah") && !fam.contains("ثمانية")) continue;
            if (low.contains("sans")) return fam;
            if (fallback == null) fallback = fam;
        }
        return fallback != null ? fallback : Font.DIALOG;
    }

    static File codeDir() {
        try {
            return new File(EmToolGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return new File(".");
        }
    }

    // widgets

    class SectionTitle extends JComponent {
        final String num;
        String text = "";
        int count = -1;
        SectionTitle(String num) { this.num = num; setPreferredSize(new Dimension(100, 24)); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean rtl = !getComponentOrientation().isLeftToRight();
            int w = getWidth(), y = 17;
            String label = arabic ? text : text.toUpperCase();

            g2.setFont(MONO_BOLD);
            FontMetrics nm = g2.getFontMetrics();
            int numW = nm.stringWidth(num);
            g2.setFont(getFont());
            FontMetrics tm = g2.getFontMetrics();
            int txtW = tm.stringWidth(label);
            String cnt = count < 0 ? "" : String.valueOf(count);
            int cntW = nm.stringWidth(cnt);

            int numX = rtl ? w - numW : 0;
            int txtX = rtl ? numX - 10 - txtW : numW + 10;
            int lineA = rtl ? cntW + (cnt.isEmpty() ? 0 : 10) : txtX + txtW + 10;
            int lineB = rtl ? txtX - 10 : w - cntW - (cnt.isEmpty() ? 0 : 10);
            int cntX = rtl ? 0 : w - cntW;

            g2.setFont(MONO_BOLD);
            g2.setColor(RUST);
            g2.drawString(num, numX, y);
            g2.setFont(getFont());
            g2.setColor(INK);
            g2.drawString(label, txtX, y);
            g2.setColor(RULE);
            if (lineB > lineA) g2.drawLine(lineA, y - 4, lineB, y - 4);
            if (!cnt.isEmpty()) {
                g2.setFont(MONO_BOLD);
                g2.setColor(count > 0 ? RUST : MUTED);
                g2.drawString(cnt, cntX, y);
            }
            g2.dispose();
        }
    }

    static class FlatButton extends JButton {
        final boolean primary;
        boolean hover;
        FlatButton(boolean primary) {
            this.primary = primary;
            setFont(UI_BOLD);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 16, 7, 16));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color fg;
            if (!isEnabled()) {
                g2.setColor(RULE);
                g2.drawRect(0, 0, w - 1, h - 1);
                fg = MUTED;
            } else if (primary) {
                g2.setColor(hover ? RUST : INK);
                g2.fillRect(0, 0, w, h);
                fg = SHEET;
            } else {
                if (hover) { g2.setColor(INK); g2.fillRect(0, 0, w, h); }
                g2.setColor(INK);
                g2.drawRect(0, 0, w - 1, h - 1);
                fg = hover ? SHEET : INK;
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(fg);
            String s = getText();
            g2.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    class Toggle extends JComponent {
        final java.util.function.Supplier<String> left, right;
        final java.util.function.BooleanSupplier rightOn;

        Toggle(java.util.function.Supplier<String> left, java.util.function.Supplier<String> right,
               java.util.function.BooleanSupplier rightOn, Runnable flip) {
            this.left = left; this.right = right; this.rightOn = rightOn;
            setPreferredSize(new Dimension(110, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { flip.run(); }
            });
        }

        Font fontFor(String s) { return s.matches(".*[\\u0600-\\u06FF].*") ? UI_TITLE : MONO_BOLD; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String a = left.get(), b = right.get();
            boolean on = rightOn.getAsBoolean();
            Font fa = fontFor(a), fb = fontFor(b);
            int y = 18, gap = 22;
            g2.setFont(fa);
            int aW = g2.getFontMetrics().stringWidth(a);
            g2.setFont(fb);
            int bW = g2.getFontMetrics().stringWidth(b);
            int x0 = (getWidth() - aW - gap - bW) / 2;
            int bX = x0 + aW + gap;

            g2.setFont(fa);
            g2.setColor(on ? MUTED : INK);
            g2.drawString(a, x0, y);
            g2.setColor(RULE);
            g2.drawLine(x0 + aW + gap / 2, 5, x0 + aW + gap / 2, 22);
            g2.setFont(fb);
            g2.setColor(on ? INK : MUTED);
            g2.drawString(b, bX, y);

            g2.setColor(RUST);
            if (on) g2.fillRect(bX, 25, bW, 2);
            else g2.fillRect(x0, 25, aW, 2);
            g2.dispose();
        }
    }

    class CopyList extends JList<String[]> {
        final DefaultListModel<String[]> model = new DefaultListModel<>();
        String placeholder = "";

        CopyList(Color text) {
            setModel(model);
            setFont(MONO);
            setBackground(SHEET);
            setForeground(text);
            setSelectionBackground(INK);
            setSelectionForeground(SHEET);
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, "", index, sel, false);
                    String[] row = (String[]) value;
                    String muted = hex(sel ? RULE : MUTED);
                    StringBuilder h = new StringBuilder("<html>\u200E<span style='color:" + muted + "'>")
                        .append(String.format("%02d", index + 1)).append("</span>&nbsp;&nbsp;").append(esc(row[0]));
                    if (row[1] != null)
                        h.append("<br><span style='color:").append(muted)
                         .append(row[1].matches(".*[\\u0600-\\u06FF].*") ? ";font-family:" + UI_FAMILY : "")
                         .append("'><font size='2'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                         .append(esc(row[1])).append("</font></span>");
                    l.setText(h.append("</html>").toString());
                    l.setBorder(new EmptyBorder(5, 10, 5, 10));
                    l.setFont(MONO);
                    if (!sel) { l.setBackground(SHEET); l.setForeground(text); }
                    return l;
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && getSelectedValue() != null) {
                        copy(getSelectedValue()[2]);
                        setStatus("copied", "  " + getSelectedValue()[2], false);
                    }
                }
            });
        }

        void add(String main, String sub, String copyValue) { model.addElement(new String[]{main, sub, copyValue}); }
        void clear() { model.clear(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (model.isEmpty() && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(UI_SMALL);
                g2.setColor(MUTED);
                int tw = g2.getFontMetrics().stringWidth(placeholder);
                g2.drawString(placeholder, arabic ? getWidth() - tw - 12 : 12, 22);
                g2.dispose();
            }
        }
    }

    static JButton zeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        return b;
    }

    static boolean isPrivate(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }

    class FileDrop extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.stringFlavor);
        }
        @Override @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport s) {
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) return false;
                    pathField.setText(files.get(0).getAbsolutePath());
                } else {
                    pathField.setText(((String) s.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim());
                }
                analyze();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            EmToolGUI w = new EmToolGUI();
            if (args.length > 0) { w.pathField.setText(args[0]); w.analyze(); }
            w.setVisible(true);
        });
    }
}
