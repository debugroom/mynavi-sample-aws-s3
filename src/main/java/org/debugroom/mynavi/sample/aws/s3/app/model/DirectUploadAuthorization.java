package org.debugroom.mynavi.sample.aws.s3.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DirectUploadAuthorization implements Serializable {

    private String objectKey;
    private String acl;
    private String uploadUrl;
    private String policy;
    private String securityToken;
    private String date;
    private String algorithm;
    private String credential;
    private String signature;
    private String fileSizeLimit;

}
