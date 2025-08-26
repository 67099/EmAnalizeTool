import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.SharedFileInputStream;

public class EmTool {

    public static void main(String[] args) {
        System.out.println("oooooooooooo                              o8o  oooo             .o.                    .    o8o   .o88o.                         .        ooooo   ooooo                             .o8  oooo                     \r\n" + //
                        "`888'     `8                              `\"'  `888            .888.                 .o8    `\"'   888 `\"                       .o8        `888'   `888'                            \"888  `888                     \r\n" + //
                        " 888         ooo. .oo.  .oo.    .oooo.   oooo   888           .8\"888.     oooo d8b .o888oo oooo  o888oo   .oooo.    .ooooo.  .o888oo       888     888   .oooo.   ooo. .oo.    .oooo888   888   .ooooo.  oooo d8b \r\n" + //
                        " 888oooo8    `888P\"Y88bP\"Y88b  `P  )88b  `888   888          .8' `888.    `888\"\"8P   888   `888   888    `P  )88b  d88' `\"Y8   888         888ooooo888  `P  )88b  `888P\"Y88b  d88' `888   888  d88' `88b `888\"\"8P \r\n" + //
                        " 888    \"     888   888   888   .oP\"888   888   888         .88ooo8888.    888       888    888   888     .oP\"888  888         888         888     888   .oP\"888   888   888  888   888   888  888ooo888  888     \r\n" + //
                        " 888       o  888   888   888  d8(  888   888   888        .8'     `888.   888       888 .  888   888    d8(  888  888   .o8   888 .       888     888  d8(  888   888   888  888   888   888  888    .o  888     \r\n" + //
                        "o888ooooood8 o888o o888o o888o `Y888\"\"8o o888o o888o      o88o     o8888o d888b      \"888\" o888o o888o   `Y888\"\"8o `Y8bod8P'   \"888\"      o888o   o888o `Y888\"\"8o o888o o888o `Y8bod88P\" o888o `Y8bod8P' d888b ");
        try {
            boolean AskingForPathloop = true;
            Scanner sc = new Scanner(System.in);
            File emlFile = null;
            while(AskingForPathloop){ 
            System.out.print("\n enter path to eml file: ");
            String path = sc.nextLine().trim();
            sc.close();
             emlFile = new File(path);
            if (!emlFile.exists()) {
                System.out.println("Error 404 |E!");
                }
            else {AskingForPathloop = false;
        }
    }//end loop 

            //Jakarta Mail Library method to read EML file 
            Session session = Session.getDefaultInstance(new Properties());
            SharedFileInputStream fis = new SharedFileInputStream(emlFile);
            MimeMessage msg = new MimeMessage(session, fis);

            //extract the Headers
            String subj = msg.getSubject();
            String from = Arrays.toString(msg.getFrom());
            String to = Arrays.toString(msg.getRecipients(Message.RecipientType.TO));
            String returnPath = msg.getHeader("Return-Path", null);
            String msgId = msg.getHeader("Message-ID", null);
            String receivedSpf = msg.getHeader("Received-SPF", null);

            System.out.println("\n---Email Artifacts---");
            System.out.println("|  Subject: " + subj);
            System.out.println("|  From: " + from);
            System.out.println("|  To: " + to);
            System.out.println("|  Return-Path: " + returnPath);
            System.out.println("|  Message-ID: " + msgId);
            System.out.println("|  SPF: " + (receivedSpf != null ? receivedSpf : "not found "));

            String body = getTextFromMessage(msg);

            // extract all links and defang
            List<String> links = extractAndDefangUrls(body);

            System.out.println("\n--- links (Defanged) ---");
            if (links.isEmpty()) System.out.println("No URLS found");
            
            else {
                for (String link : links) {
                    System.out.println(link);
                }
            }

            fis.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // esxtract text from the email message
    private static String getTextFromMessage(Message msg) throws Exception {
        if (msg.isMimeType("text/plain")) {
            return msg.getContent().toString();
        } else if (msg.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) msg.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                sb.append(getTextFromPart(part));
            }
            return sb.toString();
        }
        return "";
    }

    private static String getTextFromPart(BodyPart part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return part.getContent().toString();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                sb.append(getTextFromPart(multipart.getBodyPart(i)));
            }
            return sb.toString();
        }
        return "";
    }

    // find and defang urls ;-;
    private static List<String> extractAndDefangUrls(String text) {
        List<String> urls = new ArrayList<>();
        Pattern p = Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String original = m.group();
            urls.add(defangLink(original));
        }
        return urls;
    }

    private static String defangLink(String url) {
        return url.replace("http", "hxxp").replace(".", "[.]");
    }
    
}
