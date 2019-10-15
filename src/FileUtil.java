import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gaoshuo
 * @date 2019-09-30
 */
public class FileUtil {

    public static List<File> splitDataToSaveFile(int rows, File sourceFile, String targetDirectoryPath) {
        List<File> fileList = new ArrayList<>();
        File targetFile = new File(targetDirectoryPath);
        if (!sourceFile.exists() || rows <= 0 || sourceFile.isDirectory()) {
            return null;
        }
        if (targetFile.exists()) {
            if (!targetFile.isDirectory()) {
                return null;
            }
        } else {
            targetFile.mkdirs();
        }

        try (FileInputStream fileInputStream = new FileInputStream(sourceFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            StringBuilder stringBuilder = new StringBuilder();
            String lineStr;
            int lineNo = 1, fileNum = 1;
            while ((lineStr = bufferedReader.readLine()) != null) {
                stringBuilder.append(lineStr).append("\r\n");
                if (lineNo % rows == 0) {
                    File file = new File(targetDirectoryPath + File.separator + fileNum + sourceFile.getName());
                    writeFile(stringBuilder.toString(), file);
                    stringBuilder.delete(0, stringBuilder.length());
                    fileNum++;
                    fileList.add(file);
                }
                lineNo++;
            }
            if ((lineNo - 1) % rows != 0) {
                File file = new File(targetDirectoryPath + File.separator + fileNum + sourceFile.getName());
                writeFile(stringBuilder.toString(), file);
                fileList.add(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileList;
    }

    private static void writeFile(String text, File file) {
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter, 1024)
        ) {
            bufferedWriter.write(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getTotalLines(File file) throws IOException {
        FileReader in = new FileReader(file);
        LineNumberReader reader = new LineNumberReader(in);
        reader.skip(Long.MAX_VALUE);
        int lines = reader.getLineNumber();
        reader.close();
        return lines;
    }

}
