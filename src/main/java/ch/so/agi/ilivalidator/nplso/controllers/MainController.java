package ch.so.agi.ilivalidator.nplso.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import ch.so.agi.ilivalidator.nplso.services.IlivalidatorService;

@Controller
public class MainController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    // Folder prefix
    private static String FOLDER_PREFIX = "ilivalidator_nplso_";

    @Autowired
    private Environment env;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    IlivalidatorService ilivalidator;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index() {
        return "index";
    }

    @RequestMapping(value = "/version.txt", method = RequestMethod.GET)
    public String version() {
        return "version.txt";
    }
    
    @RequestMapping(value = "/", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> uploadFile(
            @RequestParam(name = "allObjectsAccessible", required = false) String allObjectsAccessible,
            @RequestParam(name = "configFile", required = false) String configFile,
            @RequestParam(name = "disableAreaValidation", required = false) String disableAreaValidation,
            @RequestParam(name = "file", required = true) MultipartFile uploadFile) {

        try {
            // Get the filename.
            String filename = uploadFile.getOriginalFilename();

            // If the upload button was pushed w/o choosing a file,
            // we just redirect to the starting page.
            if (uploadFile.getSize() == 0 || filename.trim().equalsIgnoreCase("") || filename == null) {
                log.warn("No file was uploaded. Redirecting to starting page.");

                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", servletContext.getContextPath());
                return new ResponseEntity<String>(headers, HttpStatus.FOUND);
            }

            // Build the local file path.
            String directory = env.getProperty("ch.so.agi.ilivalidator.uploadedFiles");

            if (directory == null) {
                directory = System.getProperty("java.io.tmpdir");
            }

            Path tmpDirectory = Files.createTempDirectory(Paths.get(directory), FOLDER_PREFIX);
            Path uploadFilePath = Paths.get(tmpDirectory.toString(), filename);

            // Save the file locally.
            byte[] bytes = uploadFile.getBytes();
            Files.write(uploadFilePath, bytes);

            // Validate transfer file with ilivalidator library.
            String inputFileName = uploadFilePath.toString();
            String baseFileName = FilenameUtils.getFullPath(inputFileName) + FilenameUtils.getBaseName(inputFileName);
            String logFileName = baseFileName + ".log";

            // The checkboxes is not exposed in the gui at the moment.
            // But we want to use the configuration file if one is present
            // and we assume that all objects are accessible.
            configFile = "on";
            allObjectsAccessible = "on";

            // Run validation.
            boolean valid = ilivalidator.validate(allObjectsAccessible, configFile, inputFileName, logFileName);

            // Send log file back to client.
            File logFile = new File(logFileName);
            InputStream is = new FileInputStream(logFile);
                        
//            String result = new String();
//            try (Stream<String> stream = Files.lines(Paths.get(logFileName))) {
//                result = stream
//                        .filter(line -> (
//                                   line.startsWith("Error:") || 
//                                   line.startsWith("Info: ...validation") ||
//                                   line.startsWith("Info: ilivalidator") ||
//                                   line.startsWith("Info: ili2c") ||
//                                   line.startsWith("Info: dataFile"))
//                                )
//                        //.map(String::toUpperCase)
//                        .collect(Collectors.joining("\n"));
//
//            } catch (IOException e) {
//                throw new Exception(e);
//            }            
            return ResponseEntity.ok().header("Content-Type", "text/plain; charset=utf-8")
                    .contentLength(logFile.length())
                    // .contentType(MediaType.parseMediaType("text/plain"))
                     .body(new InputStreamResource(is));
//                    .body(result);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return ResponseEntity.badRequest().contentType(MediaType.parseMediaType("text/plain")).body(e.getMessage());
        }
    }
}
