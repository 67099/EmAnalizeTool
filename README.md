# EmTool · محلل الإيميلات

Reads an `.eml` file and pulls out the artifacts an analyst needs: the key headers, every link (defanged so nobody clicks it by accident), and the attachment names. Runs locally, nothing is sent anywhere.

يقرأ ملف الإيميل `.eml` ويطلع لك الأدلة اللي تحتاجها في التحليل: أهم الترويسات، كل الروابط (معطّلة عشان ما أحد يضغطها بالغلط)، وأسماء المرفقات. يشتغل على جهازك بس، وما يرسل شي لأي مكان.

![EmTool](docs/screenshot-en.png)

## What it shows

| Section | What you get |
|---|---|
| Headers | Subject, From, To, Date, Return-Path, Reply-To, Message-ID, SPF result (pass in green, fail/softfail in red) |
| Links | Every `http(s)` URL from the plain-text and HTML body, de-duplicated and defanged (`hxxps://site[.]com`) |
| Attachments | File name and type of each attachment |
| Footer | SHA-256 of the `.eml` file |

Then copy the whole report or save it as a `.txt`. Double-click a single link to copy it.

The interface switches between English and Arabic from the `EN | عربي` toggle at the top; in Arabic the layout flips right-to-left while addresses and links stay left-to-right.

## Run it

You need Java 8 or newer. From inside the `EmAnalizeTool` folder:

**Windows**
```
javac -cp ".;lib/*" -encoding UTF-8 *.java
java -cp ".;lib/*" EmToolGUI
```

**macOS / Linux**
```
javac -cp ".:lib/*" -encoding UTF-8 *.java
java -cp ".:lib/*" EmToolGUI
```

Compile once; after that the `java` line is enough. Drop an `.eml` file on the window, paste its path, or press **browse**. You can also pass the path directly: `java -cp ".;lib/*" EmToolGUI path\to\mail.eml`.

The original terminal version is still there: `java -cp ".;lib/*" EmTool`.

## Why javax.mail and not plain text

`javax.mail` parses the email format directly (MIME parts, encodings, attachments). Reading the file as plain text would mean re-implementing all of that by hand.

## Files

```
EmAnalizeTool/
  EmTool.java          original command-line tool
  EmailAnalyzer.java   parsing used by the GUI
  EmToolGUI.java       the desktop window
  lib/                 javax.mail + javax.activation
```

---

## بالعربي

### وش يسوي
- يقرأ الإيميل ويطلع لك الترويسات المهمة: الموضوع، المرسل، المستلم، التاريخ، Return-Path، Reply-To، Message-ID، ونتيجة SPF (الأخضر pass، والأحمر fail أو softfail)
- يطلع كل الروابط من نص الإيميل ومن الـ HTML، بدون تكرار، ومعطّلة بهالشكل `hxxps://site[.]com`
- يعرض أسماء المرفقات ونوعها
- يحسب SHA-256 للملف
- تقدر تنسخ التقرير كامل أو تحفظه كملف نصي، ودبل كلك على أي رابط ينسخه

الواجهة فيها عربي وإنجليزي من الزر اللي فوق `EN | عربي`، وبالعربي الواجهة تنقلب من اليمين لليسار.

![EmTool بالعربي](docs/screenshot-ar.png)

### طريقة التشغيل
تحتاج Java 8 أو أحدث. افتح التيرمنل داخل فولدر `EmAnalizeTool`:

```
javac -cp ".;lib/*" -encoding UTF-8 *.java
java -cp ".;lib/*" EmToolGUI
```

أمر `javac` تسويه أول مرة بس. على ماك أو لينكس بدّل `;` بـ `:`.
بعدها اسحب ملف الإيميل على النافذة، أو الصق مساره، أو اضغط **اختر ملف**.

النسخة القديمة اللي تشتغل بالتيرمنل باقية: `java -cp ".;lib/*" EmTool`

### ليش javax.mail وما خليته txt
- مكتبة javax.mail جاهزة وتتعامل مع الإيميلات مباشرة (الأجزاء، الترميز، المرفقات)
- لو قريته كـ txt بتحتاج تعالج كل هذا بنفسك
