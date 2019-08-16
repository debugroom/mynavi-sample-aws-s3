package org.debugroom.mynavi.sample.aws.s3.app.web.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.S3Actions;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.debugroom.mynavi.sample.aws.s3.app.model.DirectUploadAuthorization;
import org.debugroom.mynavi.sample.aws.s3.app.model.PostPolicy;

@Component
public class S3DirectUploadHelper implements InitializingBean {

    private static final String RESOURCE_ARN_PREFIX = "arn:aws:s3:::";
    private static final String ALGORITHM = "HmacSHA256";

    @Value("${bucket.name}")
    private String bucketName;
    @Value("${cloud.aws.region.static}")
    private String region;
    @Value("${sts.min.duration.minutes}")
    private int stsMinDurationMinutes;
    @Value("${s3.upload.duration.seconds}")
    private int durationSeconds;
    @Value("${s3.upload.acl}")
    private String accessControlLevel;
    @Value("${s3.upload.limitBytes}")
    private String fileSizeLimit;
    @Value("${s3.upload.role.name}")
    private String roleName;
    @Value("${s3.upload.role.session.name}")
    private String roleSessionName;
    private String roleArn;

    @Autowired
    AmazonS3 amazonS3;

    @Autowired
    ObjectMapper objectMapper;

    public void createDirectory(String directoryPath){
        ObjectMetadata objectMetadata = new ObjectMetadata();
        try(InputStream emptyContent = new ByteArrayInputStream(new byte[0])){
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName, directoryPath + "/", emptyContent, objectMetadata);
            amazonS3.putObject(putObjectRequest);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

   /**
     * ブラウザからPOSTメソッドでダイレクトアップロードするために必要な認証情報を作成するメソッド
     * @param uploadDirectory
     * @return
     */
    public DirectUploadAuthorization createDirectUploadAuthorization(String uploadDirectory){
        String objectKey = new StringBuilder().append(uploadDirectory).append("/").toString();
        Credentials credentials = getTemporaryCredentials(objectKey);
        DateTime nowUTC = new DateTime(DateTimeZone.UTC);
        String dateString = nowUTC.toString("yyyyMMdd");
        String credentialString = createCredentialString(credentials.getAccessKeyId(),
                dateString, region, "s3");
        String securityToken = credentials.getSessionToken();
        String uploadUrl = createUploadUrl(region);
        String algorithm = "AWS4-HMAC-SHA256";
        String iso8601dateTime = nowUTC.toString("yyyyMMdd'T'HHmmss'Z'");

        /*
         * PostPolicyの作成。ブラウザからPOSTメソッドで非公開バケットへアップロードを行う場合、
         * リクエストにPOSTポリシーを含める必要がある。POSTポリシーは一時認証情報に加え、
         * アップロードファイルの上限やアップロードファイルのオブジェクトキーパターン、
         * メタデータ(オブジェクトに付加するデータ)の情報を追加する。POSTポリシーに含まれていない
         * 情報がアップロードリクエストに含まれていた場合、アップロードに失敗するので注意。
         * 書式 : https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-post-example.html
         *       https://docs.aws.amazon.com/ja_jp/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html
         */
        PostPolicy postPolicy = PostPolicy.builder()
                .expiration(nowUTC.plusSeconds(durationSeconds).toString()) //アップロード有効期限
                .conditions(new String[][]{
					{"eq", "$bucket", bucketName},                         // バケット名が完全一致
                    {"starts-with", "$key", objectKey},              // オブジェクトキー名が前方一致
                    {"eq", "$acl", accessControlLevel},                    // ACLが完全一致
                    {"eq", "$x-amz-credential", credentialString},         // 認証情報が完全一致
                    {"eq", "$x-amz-security-token", securityToken},        // セキュリティトークンが完全一致
                    {"eq", "$x-amz-algorithm", algorithm},                 // アルゴリズムが完全一致
                    {"eq", "$x-amz-date", iso8601dateTime},                // 日付書式が完全一致
                    {"content-length-range", "0", fileSizeLimit},          // ファイル上限サイズを指定
                })
                .build();

        String policyDocument = null;
        try{
            policyDocument = objectMapper.writeValueAsString(postPolicy);
        }catch (JsonProcessingException e){
            e.printStackTrace();
        }

        /**
         * Calculating a Signature.
         * See : https://docs.aws.amazon.com/ja_jp/AmazonS3/latest/API/sigv4-authentication-HTTPPOST.html
         */
        String base64Policy = Base64.encodeBase64String(
                policyDocument.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = getSignatureKey(credentials.getSecretAccessKey(),
                dateString, region, "s3");

        // signatureをpolicyとセットで渡す必要があるため、HMAC SHA-1を使ってpolicyをSecret Keyで署名する。
        String signatureForPolicy = Hex.encodeHexString(calculateHmacSHA256(
                base64Policy, signingKey));

        return DirectUploadAuthorization.builder()
                .uploadUrl(uploadUrl)          // POSTメソッドでアクセスするアップロード先のURL
                .acl(accessControlLevel)       // アップロードしたファイルの公開範囲
                .date(iso8601dateTime)         // 日時情報(UTCTimezone ISO8601:YYYYMMDD'T'HHMMSS'Z')
                .objectKey(objectKey)    // アップロードファイルのオブジェクトキー
                .securityToken(securityToken)  // 一時的認証情報のセキュリティトークン
                .algorithm(algorithm)          // "AWS4-HMAC-SHA256"
                .credential(credentialString)  // 一時的認証情報やリージョンを含む文字列
                .signature(signatureForPolicy) // POSTポリシーに対する署名
                .policy(base64Policy)          // POSTポリシードキュメント(Base64エンコードが必要)
                .fileSizeLimit(fileSizeLimit)  // アップロードファイルサイズ上限
                .build();
    }

    private Credentials getTemporaryCredentials(String objectKey){
        String resourceArn = new StringBuilder()
                .append(RESOURCE_ARN_PREFIX)
                .append(bucketName).append("/")
                .append(objectKey).append("*")
                .toString();

        Statement statement = new Statement(Statement.Effect.Allow)
                .withActions(S3Actions.PutObject)
                .withResources(new com.amazonaws.auth.policy.Resource(resourceArn));
        String iamPolicy = new Policy().withStatements(statement).toJson();

        AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
                .withRoleArn(roleArn)
                .withDurationSeconds((int)TimeUnit.MINUTES.toSeconds(stsMinDurationMinutes))
                .withRoleSessionName(roleSessionName)
                .withPolicy(iamPolicy);
        return AWSSecurityTokenServiceClientBuilder.standard().build()
                .assumeRole(assumeRoleRequest).getCredentials();
    }

    /**
     * POSTリクエストで、X-Amz-Credentialに設定する文字列を作成する
     * 書式: https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html
     * @param accessKey
     * @param date
     * @param region
     * @param serviceName
     * @return
     */
    private String createCredentialString(String accessKey, String date, String region, String serviceName){
        return new StringBuilder()
                .append(accessKey).append("/")
                .append(date).append("/")
                .append(region).append("/")
                .append(serviceName).append("/aws4_request")
                .toString();
    }

    /**
     * アップロードするS3BucketのURLを作成する。
     * @param region
     * @return
     */
    private String createUploadUrl(String region){
        return new StringBuilder()
                .append("https://").append(bucketName).append(".s3-").append(region).append(".amazonaws.com/")
                .toString();
    }

    /**
     * シークレットキーから署名を作成するメソッド
     *  See https://docs.aws.amazon.com/ja_jp/general/latest/gr/signature-v4-examples.html#signature-v4-examples-java
     * @param key
     * @param dateStamp
     * @param region
     * @param serviceName
     * @return
     */
    private byte[] getSignatureKey(String key, String dateStamp, String region,
                                   String serviceName){
        byte[] kSecret = new StringBuilder().append("AWS4").append(key).toString()
                .getBytes(StandardCharsets.UTF_8);
        byte[] kDate = calculateHmacSHA256(dateStamp, kSecret);
        byte[] kRegion = calculateHmacSHA256(region, kDate);
        byte[] kService = calculateHmacSHA256(serviceName, kRegion);
        byte[] kSigning = calculateHmacSHA256("aws4_request", kService);
        return kSigning;
    }

    /**
     * 署名キーを使って、指定したアルゴリズム(HMAC)でハッシュ操作するメソッド
     * @param stringToSign
     * @param signingKey
     * @return
     */
    private byte[] calculateHmacSHA256(String stringToSign, byte[] signingKey){
        Mac mac = null;
        try{
            mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
        }catch (NoSuchAlgorithmException | InvalidKeyException e){
            e.printStackTrace();
        }
        return mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GetRoleRequest getRoleRequest = new GetRoleRequest().withRoleName(roleName);
        roleArn = AmazonIdentityManagementClientBuilder.standard().build()
                .getRole(getRoleRequest).getRole().getArn();
    }
}
