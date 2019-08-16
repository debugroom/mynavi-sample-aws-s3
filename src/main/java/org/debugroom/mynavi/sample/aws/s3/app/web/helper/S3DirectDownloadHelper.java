package org.debugroom.mynavi.sample.aws.s3.app.web.helper;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.S3Actions;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class S3DirectDownloadHelper implements InitializingBean {

    private static final String RESOURCE_ARN_PREFIX = "arn:aws:s3:::";
    private static final String DIRECTORY_DELIMITER = "/";

    @Value("${bucket.name}")
    private String bucketName;

    @Value("${sts.min.duration.minutes}")
    private int stsMinDurationMinutes;

    @Value("${s3.download.role.name}")
    private String roleName;
    @Value("${s3.download.role.session.name}")
    private String roleSessionName;
    @Value("${s3.download.duration.seconds}")
    private int durationSeconds;
    private String roleArn;

    public URL getPresignedUrl(String filePath){
        AmazonS3 amazonS3 = getS3ClientWithDownloadPolicy(filePath);
        Date expiration = Date.from(ZonedDateTime.now().plusSeconds(durationSeconds).toInstant());
        return amazonS3.generatePresignedUrl(bucketName, filePath, expiration);
    }

    public URL getDownloadPresignedUrl(String filePath){
        AmazonS3 amazonS3 = getS3ClientWithDownloadPolicy(filePath);
        Date expiration = Date.from(ZonedDateTime.now().plusSeconds(durationSeconds).toInstant());
        String fileName = StringUtils.substringAfterLast(filePath, DIRECTORY_DELIMITER);
        ResponseHeaderOverrides responseHeaderOverrides = new ResponseHeaderOverrides();
        responseHeaderOverrides.withContentDisposition(new StringBuilder()
                .append("attachment;filename=")
                .append("".equals(fileName) ? filePath : fileName)
                .toString());
        GeneratePresignedUrlRequest generatePresignedUrlRequest
                = new GeneratePresignedUrlRequest(bucketName, filePath, HttpMethod.GET);
        generatePresignedUrlRequest.withExpiration(expiration);
        generatePresignedUrlRequest.withResponseHeaders(responseHeaderOverrides);
        return amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
    }

    private AmazonS3 getS3ClientWithDownloadPolicy(String objectKey){
        // Create ARN for download S3 Object.
        String resourceArn = new StringBuilder()
                .append(RESOURCE_ARN_PREFIX)
                .append(bucketName)
                .append(DIRECTORY_DELIMITER)
                .append(objectKey)
                .toString();
        // Create IAM Policy provided temporary security credentials.
        Statement statement = new Statement(Statement.Effect.Allow)
                .withActions(S3Actions.GetObject)
                .withResources(new com.amazonaws.auth.policy.Resource(resourceArn));
        String iamPolicy = new Policy().withStatements(statement).toJson();

        // return S3Client with setting above iam policy.
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new STSAssumeRoleSessionCredentialsProvider
                        .Builder(roleArn, roleSessionName)
                        .withRoleSessionDurationSeconds(
                                (int)TimeUnit.MINUTES.toSeconds(stsMinDurationMinutes))
                        .withScopeDownPolicy(iamPolicy)
                        .build())
                .build();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GetRoleRequest getRoleRequest = new GetRoleRequest().withRoleName(roleName);
        roleArn = AmazonIdentityManagementClientBuilder.standard().build()
                .getRole(getRoleRequest).getRole().getArn();
    }

}
