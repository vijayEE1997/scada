import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoosePATH {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java GoosePATH <folderPath>");
            System.exit(1);
        }

        String folderPath = args[0];
        Path folder = Paths.get(folderPath);
        Path resultFilePath = folder.resolve("result.txt");
        Path csvFilePath = folder.resolve("result.csv");

        // Collect and sort all .cid / .CID files alphabetically for deterministic
        // output
        List<Path> cidFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.toLowerCase().endsWith(".cid")) {
                    cidFiles.add(entry);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        cidFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

        // Write legacy result.txt and structured result.csv alongside
        try (BufferedWriter writer = Files.newBufferedWriter(resultFilePath);
                BufferedWriter csvWriter = Files.newBufferedWriter(csvFilePath)) {

            // CSV Header
            csvWriter.write(
                    "Subscriber_IED,Internal_Signal,Publisher_IED,ldInst,prefix,lnClass,lnInst,daName,doName,Full_Publisher_Reference");
            csvWriter.newLine();

            for (Path entry : cidFiles) {
                String result = "";
                String fileName = entry.getFileName().toString();
                String fileContent = readFile(entry.toString());

                if (fileName.startsWith("REL")) {
                    String relayHeader = fileName.replaceFirst("(?i)\\.cid$", "");
                    writer.write(relayHeader);
                    writer.newLine();
                    writer.newLine();
                    result = parseXMLAttributesABB(fileContent, relayHeader, csvWriter);
                } else if (fileName.startsWith("P")) {
                    result = parseXMLAttributesGE(fileContent, csvWriter);
                }

                if (result.length() > 0) {
                    writer.write(result);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    public static String parseXMLAttributesABB(String input) {
        return parseXMLAttributesABB(input, "", null);
    }

    public static String parseXMLAttributesABB(String input, String subscriberIED, BufferedWriter csvWriter) {
        StringBuilder result = new StringBuilder();

        Pattern extRefPattern1 = Pattern.compile("<Bay name([^>]*)>");
        Matcher extRefMatcher1 = extRefPattern1.matcher(input);
        while (extRefMatcher1.find()) {
            String element = extRefMatcher1.group(1);
            String desc = extractValue(element, "desc");
            result.append(desc).append("\n\n");
        }

        Pattern extRefPattern = Pattern.compile("<ExtRef ([^>]*)/>");
        Matcher extRefMatcher = extRefPattern.matcher(input);

        while (extRefMatcher.find()) {
            String element = extRefMatcher.group(1);

            String iedName = extractValue(element, "iedName");
            String ldInst = extractValue(element, "ldInst");
            String prefix = extractValue(element, "prefix");
            String lnClass = extractValue(element, "lnClass");
            String daName = extractValue(element, "daName");
            String doName = extractValue(element, "doName");
            String intAddr = extractValue(element, "intAddr");
            String lnInst = extractValue(element, "lnInst");

            if (intAddr.contains("140,13")) {
                int startIndex = intAddr.indexOf("App1.");
                if (startIndex != -1) {
                    startIndex += "App1.".length();
                    int endIndex = intAddr.indexOf(".Value", startIndex);
                    if (endIndex != -1) {
                        String resu = intAddr.substring(startIndex, endIndex);
                        String pubRef = iedName + "/" + ldInst + "/" + prefix + "/" + lnClass + lnInst + "/" + daName
                                + "/" + doName;

                        result.append(resu).append(" = ").append(pubRef).append("\n");

                        if (csvWriter != null) {
                            writeCsvRow(csvWriter, subscriberIED, resu, iedName, ldInst, prefix, lnClass, lnInst,
                                    daName, doName, pubRef);
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    public static String parseXMLAttributesGE(String input) {
        return parseXMLAttributesGE(input, null);
    }

    public static String parseXMLAttributesGE(String input, BufferedWriter csvWriter) {
        StringBuilder result = new StringBuilder();

        Pattern extRefPattern1 = Pattern.compile("<ConnectedAP ([^>]*)>");
        Matcher extRefMatcher1 = extRefPattern1.matcher(input);
        String subscriberIED = "";
        int a = 1;
        while (extRefMatcher1.find()) {
            if (a == 1) {
                String element = extRefMatcher1.group(1);
                subscriberIED = extractValue(element, "iedName");
                result.append(subscriberIED).append("\n\n");
                a = 0;
            }
        }

        Pattern extRefPattern = Pattern.compile("<ExtRef ([^>]*)/>");
        Matcher extRefMatcher = extRefPattern.matcher(input);

        while (extRefMatcher.find()) {
            String element = extRefMatcher.group(1);

            String iedName = extractValue(element, "iedName");
            String ldInst = extractValue(element, "ldInst");
            String prefix = extractValue(element, "prefix");
            String lnClass = extractValue(element, "lnClass");
            String daName = extractValue(element, "daName");
            String doName = extractValue(element, "doName");
            String intAddr = extractValue(element, "intAddr");
            String lnInst = extractValue(element, "lnInst");

            String pubRef = iedName + "/" + ldInst + "/" + prefix + "/" + lnClass + lnInst + "/" + daName + "/"
                    + doName;
            result.append(intAddr).append(" = ").append(pubRef).append("\n");

            if (csvWriter != null) {
                writeCsvRow(csvWriter, subscriberIED, intAddr, iedName, ldInst, prefix, lnClass, lnInst, daName, doName,
                        pubRef);
            }
        }

        return result.toString();
    }

    public static String extractValue(String input, String attributeName) {
        String regex = attributeName + "=\"([^\"]*)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static void writeCsvRow(BufferedWriter csvWriter, String subscriberIED, String intAddr,
            String iedName, String ldInst, String prefix, String lnClass,
            String lnInst, String daName, String doName, String pubRef) {
        try {
            csvWriter.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                    escapeCsv(subscriberIED),
                    escapeCsv(intAddr),
                    escapeCsv(iedName),
                    escapeCsv(ldInst),
                    escapeCsv(prefix),
                    escapeCsv(lnClass),
                    escapeCsv(lnInst),
                    escapeCsv(daName),
                    escapeCsv(doName),
                    escapeCsv(pubRef)));
            csvWriter.newLine();
        } catch (IOException ignored) {
        }
    }

    private static String escapeCsv(String value) {
        if (value == null)
            return "";
        return value.replace("\"", "\"\"");
    }
}
