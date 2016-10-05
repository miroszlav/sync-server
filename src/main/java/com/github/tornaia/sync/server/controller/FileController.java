package com.github.tornaia.sync.server.controller;

import com.github.tornaia.sync.server.data.document.File;
import com.github.tornaia.sync.server.data.repository.FileRepository;
import com.github.tornaia.sync.server.websocket.SyncWebSocketHandler;
import com.github.tornaia.sync.shared.api.FileMetaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger LOG = LoggerFactory.getLogger(FileController.class);

    @Resource
    private FileRepository fileRepo;

    @Autowired
    private SyncWebSocketHandler syncWebSocketHandler;

    @RequestMapping(path = "/reset", method = RequestMethod.GET)
    public void resetDatabase() {
        List<File> files = fileRepo.findAll();
        files.forEach(fileRepo::delete);
        LOG.info("Just all files from DB: " + files.size());
    }

    @RequestMapping(method = RequestMethod.GET)
    public List<FileMetaInfo> getModifiedFiles(@RequestParam("userid") String userid, @RequestParam("modificationDateTime") long modTs) {
        List<FileMetaInfo> result = new ArrayList<>();
        List<File> fileList = fileRepo.findAllModified(userid, modTs);
        if (!Objects.isNull(fileList)) {
            result.addAll(fileList.stream().map(file -> new FileMetaInfo(file.getId(), userid, file.getPath(), file.getData().length, file.getCreationDate(), file.getLastModifiedDate())).collect(Collectors.toList()));
        }
        return result;
    }

    @RequestMapping(method = RequestMethod.POST)
    public FileMetaInfo postFile(@RequestParam("userid") String userid, @RequestParam("creationDateTime") long creationDateTime, @RequestParam("modificationDateTime") long modificationDateTime, @RequestPart("file") MultipartFile multipartFile) throws IOException {
        String path = multipartFile.getOriginalFilename();
        File file = fileRepo.findByPath(path);
        if (file == null) {
            file = fileRepo.insert(new File(userid, path, multipartFile.getBytes(), creationDateTime, modificationDateTime));
            FileMetaInfo fileMetaInfo = new FileMetaInfo(file.getId(), userid, path, file.getData().length, file.getCreationDate(), file.getLastModifiedDate());
            LOG.info("POST file: " + fileMetaInfo);

            // TODO see SyncWebSocketHandler.notifyClients
            new Thread(() -> {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                syncWebSocketHandler.notifyClients(fileMetaInfo);
            }).start();
            return fileMetaInfo;
        }
        throw new FileAlreadyExistsException(path);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public FileMetaInfo putFile(@PathVariable String id, @RequestParam("userid") String userid, @RequestParam("creationDateTime") long creationDateTime, @RequestParam("modificationDateTime") long modificationDateTime, @RequestPart("file") MultipartFile multipartFile) throws IOException {
        String path = multipartFile.getOriginalFilename();
        File file = fileRepo.findOne(id);
        if (file == null) {
            throw new FileNotFoundException(path);
        } else {
            file.setCreationDate(creationDateTime);
            file.setLastModifiedDate(modificationDateTime);
            file.setData(multipartFile.getBytes());
            fileRepo.save(file);
        }

        FileMetaInfo fileMetaInfo = new FileMetaInfo(file.getId(), userid, path, file.getData().length, file.getCreationDate(), file.getLastModifiedDate());
        LOG.info("PUT file: " + fileMetaInfo);
        return fileMetaInfo;
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteFile(@PathVariable String id) throws IOException {
        File file = fileRepo.findOne(id);
        if (file == null) {
            throw new FileNotFoundException(id);
        }
        String path = file.getPath();
        fileRepo.delete(file);
        LOG.info("DELETE file: " + path);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM)
    public ResponseEntity getFile(@PathVariable String id, @RequestParam("userid") String userid) throws IOException {
        File file = fileRepo.findOne(id);
        if (file == null) {
            throw new FileNotFoundException(id);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());
        LOG.info("GET file: " + file.getPath());
        return new ResponseEntity<>(file.getData(), responseHeaders, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}/metaInfo", method = RequestMethod.GET)
    public FileMetaInfo getMetaInfo(@PathVariable String id, @RequestParam("userid") String userid) throws IOException {
        File file = fileRepo.findOne(id);
        if (file == null) {
            throw new FileNotFoundException(id);
        }

        FileMetaInfo fileMetaInfo = new FileMetaInfo(file.getId(), file.getUserid(), file.getPath(), file.getData().length, file.getCreationDate(), file.getLastModifiedDate());
        LOG.info("GET metaInfo: " + fileMetaInfo);
        return fileMetaInfo;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private class FileNotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        FileNotFoundException(String path) {
            super("Could not find file: " + path);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private class FileAlreadyExistsException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        FileAlreadyExistsException(String path) {
            super("File already exists: " + path);
        }
    }
}
