<h1 align="center">EmTool</h1>

<p align="center">
  Email artifact extractor for phishing triage<br>
  <a href="#ايميل-مشبوه-">العربية</a> · <a href="#english">English</a>
</p>

<p align="center">
  <img alt="Java 8+" src="https://img.shields.io/badge/Java-8%2B-1D1A17?style=flat-square&labelColor=141210">
  <img alt="Swing" src="https://img.shields.io/badge/UI-Swing-1D1A17?style=flat-square&labelColor=141210">
  <img alt="javax.mail" src="https://img.shields.io/badge/parser-javax.mail%201.6.2-1D1A17?style=flat-square&labelColor=141210">
  <img alt="Offline" src="https://img.shields.io/badge/runs-offline-E0673F?style=flat-square&labelColor=141210">
</p>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/screenshot-en-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="docs/screenshot-en-light.png">
  <img alt="EmTool main window" src="docs/screenshot-en-dark.png">
</picture>

---

<div dir="rtl">

## ايميل مشبوه ؟ 

حط ملف `.eml` وتطلع لك كل معلوماته في شاشة وحدة. كل شي يصير على جهازك وما يطلع شي للنت.

### وش تطلع لك

| القسم | المحتوى |
|---|---|
| معلومات الإيميل | الموضوع، المرسل، المستلم، التاريخ، Return-Path، Reply-To، Message-ID، X-Mailer |
| فحص المرسل | SPF و DKIM و DMARC |
| تنبيه | لو Return-Path أو Reply-To غير دومين المرسل |
| الروابط | كل الروابط، معطّلة: `hxxps://site[.]com` |
| المرفقات | الاسم والنوع والحجم و SHA-256 |
| عناوين IP | السيرفرات اللي مر عليها الإيميل |

دبل كلك على أي سطر ينسخه. فيه وضع داكن وفاتح، وعربي وإنجليزي.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/screenshot-ar-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="docs/screenshot-ar-light.png">
  <img alt="EmTool بالعربي" src="docs/screenshot-ar-dark.png">
</picture>

### التشغيل

تحتاج Java 8 أو أحدث. افتح التيرمنل داخل فولدر `EmAnalizeTool`:

</div>

```
javac -cp ".;lib/*" -encoding UTF-8 *.java
java  -cp ".;lib/*" EmToolGUI
```

<div dir="rtl">

على ماك أو لينكس بدّل `;` بـ `:`. النسخة الأصلية حقت التيرمنل: `java -cp ".;lib/*" EmTool`

### الخط

الأداة على خط ثمانية. نزّله من موقع ثمانية وحط ملفاته في `EmAnalizeTool/fonts/`. ما هو في الريبو لأن الرخصة تمنع نشره.

### ليش javax.mail

تقرأ الإيميل مباشرة بكل أجزائه ومرفقاته، بدل ما نعالجه كنص.

</div>

---

## English

Drop in an `.eml` file and get everything about it on one screen. Runs locally, nothing leaves your machine.

### What it extracts

| Section | Content |
|---|---|
| Email info | Subject, From, To, Date, Return-Path, Reply-To, Message-ID, X-Mailer |
| Sender checks | SPF, DKIM, DMARC |
| Warning | Return-Path or Reply-To on a different domain than the sender |
| Links | Every link, defanged: `hxxps://site[.]com` |
| Attachments | Name, type, size, SHA-256 |
| Server IPs | Servers the email passed through |

Double-click any line to copy it. Dark and light themes, English and Arabic.

### Run

Java 8 or newer. From inside the `EmAnalizeTool` folder:

```
# Windows
javac -cp ".;lib/*" -encoding UTF-8 *.java
java  -cp ".;lib/*" EmToolGUI

# macOS / Linux
javac -cp ".:lib/*" -encoding UTF-8 *.java
java  -cp ".:lib/*" EmToolGUI
```

The original command-line tool: `java -cp ".;lib/*" EmTool`

### Font

Uses the Thmanyah typeface. Download it from Thmanyah and put the files in `EmAnalizeTool/fonts/`. Not bundled, the license forbids redistribution.

### How it works

```
.eml ──> javax.mail MimeMessage
          ├─ headers ─────────> email info, SPF / DKIM / DMARC, domain check
          ├─ Received chain ──> server IPs, sender first
          └─ MIME tree
               ├─ text/plain, text/html ──> URL regex ──> defang
               └─ named parts ────────────> name, type, size, SHA-256
```

### Project layout

```
EmAnalizeTool/
  EmTool.java          original command-line tool
  EmailAnalyzer.java   parsing behind the GUI
  EmToolGUI.java       desktop window
  lib/                 javax.mail, javax.activation
docs/                  screenshots
```
