package org.debugroom.mynavi.sample.aws.s3.app.web;

import java.awt.image.BufferedImage;
import java.util.UUID;

import org.debugroom.mynavi.sample.aws.s3.app.model.DirectUploadAuthorization;
import org.debugroom.mynavi.sample.aws.s3.app.web.helper.S3DirectUploadHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import org.debugroom.mynavi.sample.aws.s3.app.model.FileUploadForm;
import org.debugroom.mynavi.sample.aws.s3.app.web.helper.S3DirectDownloadHelper;
import org.debugroom.mynavi.sample.aws.s3.app.web.helper.S3UploadHelper;
import org.debugroom.mynavi.sample.aws.s3.app.web.helper.S3DownloadHelper;

@Controller
public class SampleController {

    @Autowired
    S3DownloadHelper s3DownloadHelper;

    @Autowired
    S3UploadHelper s3UploadHelper;

    @Autowired
    S3DirectDownloadHelper s3DirectDownloadHelper;

    @Autowired
    S3DirectUploadHelper s3DirectUploadHelper;

    @GetMapping(value = "/image",
            headers = "Accept=image/jpeg, image/jpg, image/png, image/gif",
            produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_GIF_VALUE})
    @ResponseBody
    public ResponseEntity<BufferedImage> getImage(){
        return ResponseEntity.ok().body(
                s3DownloadHelper.getImage("sample.jpg"));
    }

    @GetMapping("/index.html")
    public String index(){
        return "forward:portal";
    }

    @GetMapping("portal")
    public String portal(Model model){
        model.addAttribute("imageUrl",
                s3DirectDownloadHelper.getPresignedUrl("sample.jpg").toString());
        return "portal";
    }

    @GetMapping("getTextFileBody")
    @ResponseBody
    public ResponseEntity<String> getTextFileBody(){
        return ResponseEntity.ok().body(
                s3DownloadHelper.getTextFileBody("test.txt"));
    }

    @PostMapping("upload")
    public String upload(FileUploadForm fileUploadModel){
        s3UploadHelper.saveFile(fileUploadModel.getUploadFile());
        return "redirect:/uploadResult.html";
    }

    @GetMapping("downloadFile")
    @ResponseBody
    public ResponseEntity<String> downloadFile(){
        return ResponseEntity.ok().body(
                s3DirectDownloadHelper.getDownloadPresignedUrl("test.txt").toString());
    }

    @GetMapping("/uploadResult.html")
    public String uploadResult(){
        return "uploadResult";
    }

    @GetMapping("/upload/authorization")
    @ResponseBody
    public ResponseEntity<DirectUploadAuthorization>
            getDirectUploadAuthorization(){
        String fileUploadPath = UUID.randomUUID().toString();
        s3DirectUploadHelper.createDirectory(fileUploadPath);
        return ResponseEntity.status(HttpStatus.OK).body(
                s3DirectUploadHelper.createDirectUploadAuthorization(
                fileUploadPath));
    }

}
