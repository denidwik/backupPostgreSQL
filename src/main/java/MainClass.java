import org.apache.log4j.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class MainClass {

    private static final Logger logger = Logger.getLogger(MainClass.class);
    private static Appender fh = null;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

    private static final String[] splitDatabase = Constant.DATABASE_URL.split("/");
    private static File backupFilePath;

    public static void main(String[] args) throws IOException {
        backupFilePath = new File(Constant.FILE_BACKUP + splitDatabase[3]);
        if (!backupFilePath.exists()) {
            File dir = backupFilePath;
            dir.mkdirs();
        }
        File fileLog = new File(backupFilePath + File.separator + "log");
        if (!fileLog.exists()) {
            File dir = fileLog;
            dir.mkdirs();
        }
        fileLog = new File(backupFilePath + File.separator + "log" + File.separator + "logBackup-" + sdf.format(new Date()) +".log");
        FileOutputStream is = new FileOutputStream(fileLog);
        OutputStreamWriter osw = new OutputStreamWriter(is);
        if (!fileLog.exists()) {
            File dir = fileLog;
            dir.mkdirs();
        }
        fh = new FileAppender(new SimpleLayout(), fileLog.getPath());
        PatternLayout pl = new PatternLayout("%d{HH:mm:ss,SSS} %-5p [%.20t] %-14.14c{1} %m%n");
        fh.setLayout(pl);
        logger.addAppender(fh);
        logger.info("Execute Time "+ new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
        backupDb();
        logger.info("Final Time " + new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
    }

    private static void backupDb() {
        Boolean succes = false;
        try{
            String backupFileName = "backup_" + splitDatabase[3] + "_" + sdf.format(new Date()) + ".sql";

            List<String> commands = getPgComands(splitDatabase[3], backupFilePath, backupFileName, "backup");
            if (!commands.isEmpty()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(commands);
                    pb.environment().put("PGPASSWORD", Constant.DATABASE_PASSWORD);

                    Process process = pb.start();

                    try (BufferedReader buf = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()))) {
                        String line = buf.readLine();
                        while (line != null) {
                            logger.info(line);
                            System.err.println(line);
                            line = buf.readLine();
                        }
                    }

                    process.waitFor();
                    process.destroy();
                    succes = true;
                    logger.info("===> Success on " + "BACKUP DATABASE " + splitDatabase[3] + " <===");
                } catch (IOException | InterruptedException ex) {
                    logger.info("Exception: " + ex);
                }
            } else {
                logger.info("Error: Invalid params.");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        if (succes) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -2);
//            cal.add(Calendar.MINUTE, -5);
            File[] listOfFiles = backupFilePath.listFiles();
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    if (listOfFiles[i].getName().split(Pattern.quote("."))[1].equalsIgnoreCase("sql")) {
                        if (cal.getTime().compareTo(new Date(listOfFiles[i].lastModified())) > 0) {
                            logger.info("Deleted File : " + listOfFiles[i].getName());
                            listOfFiles[i].delete();
                        }
                    }
                } else if (listOfFiles[i].isDirectory()) {
                    logger.info("Directory " + listOfFiles[i].getName());
                }
            }
        }
    }

    private static List<String> getPgComands(String databaseName, File backupFilePath, String backupFileName, String type) {
        ArrayList<String> commands = new ArrayList<>();
        if ("backup".equalsIgnoreCase(type)) {
            commands.add(Constant.PG_DUMP_TEMP);
//            commands.add("-j");
//            commands.add("8");
//            commands.add("-a");
//            commands.add("--data-only");
            commands.add("-h"); //database server host
            commands.add(splitDatabase[2].split(":")[0]);
            commands.add("-p"); //database server port number
            commands.add(splitDatabase[2].split(":")[1]);
            commands.add("-U"); //connect as specified database user
            commands.add(Constant.DATABASE_USER);
            commands.add("-F"); //output file format (custom, directory, tar, plain text (default))
            commands.add("c");
            commands.add("-b"); //include large objects in dump
            commands.add("-v"); //verbose mode
            commands.add("-f"); //output file or directory name
            commands.add(backupFilePath.getAbsolutePath() + File.separator + backupFileName);
//            commands.add("--disable-triggers");
            commands.add("-d"); //database name
            commands.add(databaseName);
        } else if ("restore".equalsIgnoreCase(type)) {
            commands.add("pg_restore");
            commands.add("-h");
            commands.add("localhost");
            commands.add("-p");
            commands.add("5432");
            commands.add("-U");
            commands.add("postgres");
            commands.add("-d");
            commands.add(databaseName);
            commands.add("-v");
            commands.add(backupFilePath.getAbsolutePath() + File.separator + backupFileName);
        }
        return commands;
    }

}
