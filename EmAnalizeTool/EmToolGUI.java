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

// Desktop front-end for EmTool. Run: java -cp ".;lib/*" EmToolGUI
public class EmToolGUI extends JFrame {

    // palette: case-file paper, ink, one rust accent
    static final Color PAPER  = new Color(0xF1ECE2);
    static final Color SHEET  = new Color(0xFAF7F1);
    static final Color INK    = new Color(0x1D1B18);
    static final Color MUTED  = new Color(0x7A7266);
    static final Color RULE   = new Color(0xD6CEC0);
    static final Color RUST   = new Color(0xA8361B);
    static final Color OLIVE  = new Color(0x4F6B2E);

    static final Font MONO      = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 13);
    static final Font MARK      = new Font(Font.MONOSPACED, Font.BOLD, 30);
    static final Font UI        = new Font(Font.DIALOG, Font.PLAIN, 13);
    static final Font UI_SMALL  = new Font(Font.DIALOG, Font.PLAIN, 12);
    static final Font UI_BOLD   = new Font(Font.DIALOG, Font.BOLD, 12);

    // ---- strings ----
    static final String[][] TEXT = {
        // key,          English,                                   Arabic
        {"tagline",   "email artifact extractor",                "محلل الإيميلات واستخراج الأدلة"},
        {"drop",      "Drop a .eml file here, or",               "اسحب ملف الإيميل هنا، أو"},
        {"browse",    "browse",                                  "اختر ملف"},
        {"analyze",   "Analyze",                                 "حلّل"},
        {"headers",   "Headers",                                 "الترويسة"},
        {"links",     "Links (defanged)",                        "الروابط (معطّلة)"},
        {"attach",    "Attachments",                             "المرفقات"},
        {"nolinks",   "No URLs found.",                          "ما فيه روابط."},
        {"noattach",  "None.",                                   "لا يوجد."},
        {"copy",      "Copy report",                             "نسخ التقرير"},
        {"save",      "Save .txt",                               "حفظ كملف نصي"},
        {"ready",     "ready. pick an .eml file to start",       "جاهز. اختر ملف الإيميل عشان نبدأ"},
        {"notfound",  "Error 404 |E!  file not found",           "Error 404 |E!  الملف مو موجود"},
        {"failed",    "could not read this file: ",              "ما قدرت أقرأ الملف: "},
        {"done",      "done.",                                   "تم."},
        {"copied",    "report copied to clipboard",              "تم النسخ"},
        {"saved",     "saved to ",                               "انحفظ في "},
        {"empty",     "nothing here yet",                        "لا شيء بعد"},
        {"hint",      "double-click a link to copy it",          "دبل كلك على الرابط عشان تنسخه"},
    };
    static final Map<String, String[]> T = new HashMap<>();
    static { for (String[] r : TEXT) T.put(r[0], new String[]{r[1], r[2]}); }

    boolean arabic = false;
    String t(String k) { return T.get(k)[arabic ? 1 : 0]; }

    // ---- state / widgets ----
    EmailAnalyzer.Report report;
    final JTextField pathField = new JTextField();
    final JLabel tagline = new JLabel();
    final JLabel dropLabel = new JLabel();
    final FlatButton browseBtn = new FlatButton(false);
    final FlatButton analyzeBtn = new FlatButton(true);
    final FlatButton copyBtn = new FlatButton(false);
    final FlatButton saveBtn = new FlatButton(false);
    final LangToggle lang = new LangToggle();
    final SectionTitle hHead = new SectionTitle("01");
    final SectionTitle lHead = new SectionTitle("02");
    final SectionTitle aHead = new SectionTitle("03");
    final JPanel headerRows = new JPanel(new GridBagLayout());
    final DefaultListModel<String> linkModel = new DefaultListModel<>();
    final JList<String> linkList = new JList<>(linkModel);
    final JPanel attachBox = new JPanel();
    final JLabel status = new JLabel();
    final JLabel hashLabel = new JLabel();
    final JLabel hintLabel = new JLabel();
    String statusKey = "ready", statusExtra = "";
    boolean statusError = false;

    public EmToolGUI() {
        super("EmTool");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 600));
        setSize(1000, 680);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAPER);
        root.setBorder(new EmptyBorder(22, 28, 14, 28));
        root.add(buildTop(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);

        // whole window accepts a dropped file
        root.setTransferHandler(new FileDrop());
        pathField.setTransferHandler(new FileDrop());

        refreshText();
        renderReport();
    }

    // ---------- layout ----------

    JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel mark = new JLabel("EmTool");
        mark.setFont(MARK);
        mark.setForeground(INK);
        tagline.setFont(MONO);
        tagline.setForeground(MUTED);
        brand.add(mark);
        brand.add(tagline);
        top.add(brand, BorderLayout.LINE_START);

        JPanel langWrap = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 6));
        langWrap.setOpaque(false);
        langWrap.add(lang);
        top.add(langWrap, BorderLayout.LINE_END);

        // file strip
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
        // headers column
        headerRows.setBackground(SHEET);
        JScrollPane hScroll = plainScroll(headerRows);
        JPanel hCol = column(hHead, hScroll);

        // links column
        linkList.setFont(MONO);
        linkList.setBackground(SHEET);
        linkList.setForeground(RUST);
        linkList.setSelectionBackground(INK);
        linkList.setSelectionForeground(SHEET);
        linkList.setFixedCellHeight(26);
        linkList.setCellRenderer(new LinkRenderer());
        linkList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && linkList.getSelectedValue() != null) {
                    copy(linkList.getSelectedValue());
                    setStatus("copied", "", false);
                }
            }
        });
        JPanel lInner = new JPanel(new BorderLayout());
        lInner.setBackground(SHEET);
        lInner.add(plainScroll(linkList), BorderLayout.CENTER);
        hintLabel.setFont(UI_SMALL);
        hintLabel.setForeground(MUTED);
        hintLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        lInner.add(hintLabel, BorderLayout.SOUTH);

        attachBox.setBackground(SHEET);
        attachBox.setLayout(new BoxLayout(attachBox, BoxLayout.Y_AXIS));
        attachBox.setBorder(new EmptyBorder(8, 10, 10, 10));

        JPanel right = new JPanel(new BorderLayout(0, 16));
        right.setOpaque(false);
        right.add(column(lHead, lInner), BorderLayout.CENTER);
        right.add(column(aHead, attachBox), BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 22, 0));
        body.setOpaque(false);
        body.add(hCol);
        body.add(right);
        return body;
    }

    JComponent buildFooter() {
        JPanel f = new JPanel(new BorderLayout(12, 0));
        f.setOpaque(false);
        f.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, RULE), new EmptyBorder(10, 0, 0, 0)));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        status.setFont(MONO);
        hashLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
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
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setBackground(SHEET);
        s.getVerticalScrollBar().setUnitIncrement(14);
        return s;
    }

    // ---------- behaviour ----------

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
        headerRows.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        if (report == null) {
            String[] keys = {"Subject", "From", "To", "Date", "Return-Path", "Reply-To", "Message-ID", "SPF"};
            for (String k : keys) addRow(c, k, "");
        } else {
            for (Map.Entry<String, String> e : report.headers.entrySet()) addRow(c, e.getKey(), e.getValue());
        }
        c.gridy++; c.weighty = 1; c.gridx = 0;
        headerRows.add(Box.createGlue(), c);

        linkModel.clear();
        if (report != null) for (String l : report.links) linkModel.addElement(l);

        attachBox.removeAll();
        if (report != null && !report.attachments.isEmpty()) {
            for (String a : report.attachments) attachBox.add(monoLine(a, INK));
        } else {
            attachBox.add(monoLine(report == null ? t("empty") : t("noattach"), MUTED));
        }

        lHead.count = report == null ? -1 : report.links.size();
        aHead.count = report == null ? -1 : report.attachments.size();
        hashLabel.setText(report == null ? " " : "sha256  " + report.sha256);
        copyBtn.setEnabled(report != null);
        saveBtn.setEnabled(report != null);

        headerRows.applyComponentOrientation(getContentPane().getComponentOrientation());
        attachBox.applyComponentOrientation(getContentPane().getComponentOrientation());
        // header values are addresses and ids, keep them left-to-right
        for (Component c2 : headerRows.getComponents())
            if (c2 instanceof JTextArea) c2.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        revalidate();
        repaint();
    }

    void addRow(GridBagConstraints c, String key, String value) {
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(9, 12, 9, 10);
        JLabel k = new JLabel(key.toUpperCase());
        k.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        k.setForeground(MUTED);
        k.setPreferredSize(new Dimension(96, 16));
        headerRows.add(k, c);

        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(7, 0, 7, 12);
        JTextArea v = new JTextArea(value.isEmpty() ? "·" : value);
        v.setEditable(false);
        v.setLineWrap(true);
        v.setWrapStyleWord(false);
        v.setFont(MONO);
        v.setOpaque(false);
        v.setBorder(null);
        v.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        v.setForeground(value.isEmpty() || value.equals("-") ? MUTED : INK);
        if (key.equals("SPF") && !value.isEmpty()) {
            String low = value.toLowerCase();
            if (low.startsWith("pass")) v.setForeground(OLIVE);
            else if (low.startsWith("fail") || low.startsWith("softfail")) v.setForeground(RUST);
            v.setFont(MONO_BOLD);
        }
        headerRows.add(v, c);
        c.gridy++;

        // hairline between rows
        c.gridx = 0; c.gridwidth = 2; c.insets = new Insets(0, 12, 0, 12);
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel line = new JPanel();
        line.setBackground(RULE);
        line.setPreferredSize(new Dimension(1, 1));
        headerRows.add(line, c);
        c.gridwidth = 1;
        c.gridy++;
    }

    static JLabel monoLine(String s, Color col) {
        JLabel l = new JLabel(s);
        l.setFont(MONO);
        l.setForeground(col);
        l.setBorder(new EmptyBorder(3, 0, 3, 0));
        return l;
    }

    void setArabic(boolean ar) {
        arabic = ar;
        ComponentOrientation o = ar ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT;
        getContentPane().applyComponentOrientation(o);
        // file paths, header values and URLs stay left-to-right
        pathField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        linkList.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        refreshText();
        renderReport();
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
        hintLabel.setText(t("hint"));
        status.setText(t(statusKey) + statusExtra);
        status.setForeground(statusError ? RUST : INK);
        // Arabic reads badly in a monospaced face
        tagline.setFont(arabic ? UI : MONO);
        status.setFont(arabic ? UI : MONO);
        Font f = arabic ? new Font(Font.DIALOG, Font.BOLD, 14) : UI_BOLD;
        hHead.setFont(f); lHead.setFont(f); aHead.setFont(f);
        lang.repaint();
        repaint();
    }

    // ---------- small custom components ----------

    // "01 ─ HEADERS ─────────── 4"
    class SectionTitle extends JComponent {
        final String num;
        String text = "";
        int count = -1;
        SectionTitle(String num) { this.num = num; setPreferredSize(new Dimension(100, 22)); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean rtl = !getComponentOrientation().isLeftToRight();
            int w = getWidth(), y = 16;
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

    // square, flat button. primary = ink fill, secondary = ink outline
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

    // "EN / ع" text switch, active side underlined in rust
    class LangToggle extends JComponent {
        final String en = "EN", ar = "عربي";
        LangToggle() {
            setPreferredSize(new Dimension(96, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("English / عربي");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { setArabic(!arabic); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font fe = MONO_BOLD, fa = new Font(Font.DIALOG, Font.BOLD, 14);
            int y = 17;
            g2.setFont(fe);
            int enW = g2.getFontMetrics().stringWidth(en);
            g2.setFont(fa);
            int arW = g2.getFontMetrics().stringWidth(ar);
            int gap = 22;
            int x0 = (getWidth() - enW - gap - arW) / 2;

            g2.setFont(fe);
            g2.setColor(arabic ? MUTED : INK);
            g2.drawString(en, x0, y);
            g2.setColor(RULE);
            g2.drawLine(x0 + enW + gap / 2, 5, x0 + enW + gap / 2, 21);
            g2.setFont(fa);
            g2.setColor(arabic ? INK : MUTED);
            int arX = x0 + enW + gap;
            g2.drawString(ar, arX, y);

            g2.setColor(RUST);
            if (arabic) g2.fillRect(arX, 23, arW, 2);
            else g2.fillRect(x0, 23, enW, 2);
            g2.dispose();
        }
    }

    // numbered link rows: "01  hxxps://..."
    static class LinkRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, false);
            String n = String.format("%02d", index + 1);
            String muted = sel ? "#C9C1B3" : "#9A9184";
            String body = value.toString().replace("&", "&amp;").replace("<", "&lt;");
            l.setText("<html><span style='color:" + muted + "'>" + n + "</span>&nbsp;&nbsp;" + body + "</html>");
            l.setBorder(new EmptyBorder(0, 10, 0, 10));
            l.setFont(MONO);
            if (!sel) { l.setBackground(SHEET); l.setForeground(RUST); }
            return l;
        }
    }

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
