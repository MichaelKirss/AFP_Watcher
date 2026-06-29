package ru.venets.afp;

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatcherService {

    private static final Logger log =
            LoggerFactory.getLogger(FileWatcherService.class);
//  общий класс сервиса по копированию файлоы
    private final Path sourceDir;
    private final Path targetDir;
    private final Path ftcDir;
    private String regexFile = null;
   
    public FileWatcherService() throws Exception {
        Properties properties = new Properties();
        Path path = Paths.get("config.properties");
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            }
        log.info("Загружены параметры из файла: {}", properties);
 
        sourceDir = Paths.get(
                properties.getProperty("source.dir"));
        targetDir = Paths.get(
                properties.getProperty("target.dir")); 
        ftcDir = Paths.get(
                properties.getProperty("ftc.dir")); 
        if (!Files.exists(targetDir)){
            log.info("Стартовый каталог не найден: {}", properties.getProperty("source.dir"));
        } 
                   
        if (!Files.exists(targetDir)){
            log.info("Результирующий каталог не найден: {}", properties.getProperty("target.dir"));
        }

        if (!Files.exists(ftcDir)){
            log.info("Каталог для передачи в АБС не найден: {}", properties.getProperty("ftc.dir"));
            throw new Exception();
        }      
        regexFile = properties.getProperty("file.regex");
        }

    public void start() throws Exception {
    // старт  программы в главном потоке
   // подписка на событие появления файлов в каталоге
        WatchService watchService =
                FileSystems.getDefault().newWatchService();
        sourceDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE
        );
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind()!= StandardWatchEventKinds.ENTRY_CREATE) 
                   {
                    continue;
                   }
                 Path fileName = (Path) event.context();
                }
            key.reset();
            processExistingFiles();
            }
    }
    
    private void processExistingFiles() throws Exception {
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(sourceDir)) {
    //чтение файлов  из ключевого каталога с проверкой по маске и на блокировку файла
            for (Path file : stream) {
                processFile(file);
            }
        }
    }

    private void processFile(Path file) throws Exception {
            String fileName = file.getFileName().toString();
            if (matchesMask(fileName)) {
                  Path target = targetDir.resolve(file.getFileName());
                  Path ftc = ftcDir.resolve(file.getFileName());
                  if (Files.exists(target)) {
                    log.info("Файл уже забран из почтовой системы {}", file);
                      waitUntilReady(file); 
                      return;
                  }     
                  if (Files.exists(ftc)) {
                      log.info("Файл в АБС не забран {}", file);                    
                      waitUntilReady(file); 
                      return;
                  }
                  Files.copy(
                        file,
                        target,
                        StandardCopyOption.COPY_ATTRIBUTES
                            );
                  log.info("Скопирован из ПЦ {}", file);            
                  Files.copy(
                        file,
                        ftc,
                        StandardCopyOption.COPY_ATTRIBUTES
                            );          
                 log.info("Скопирован для передачи в АБС {}", file);

            }
    }

    private boolean matchesMask(String fileName) {
        String today =
                LocalDate.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "yyyyMMdd"));
    
        regexFile = String.format(regexFile, today);
        return fileName.matches(regexFile);
        }
    private void waitUntilReady(Path file)throws InterruptedException {
            while (true) {
                try (FileChannel ignored =
                         FileChannel.open(
                                 file,
                                 StandardOpenOption.WRITE)) {
                    return;
            }   catch (Exception ex) {
                Thread.sleep(1000);
            }
        }
    }
}
    