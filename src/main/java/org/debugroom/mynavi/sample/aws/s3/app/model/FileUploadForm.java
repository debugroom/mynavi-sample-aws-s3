package org.debugroom.mynavi.sample.aws.s3.app.model;

import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class FileUploadForm {

    private MultipartFile uploadFile;

}
