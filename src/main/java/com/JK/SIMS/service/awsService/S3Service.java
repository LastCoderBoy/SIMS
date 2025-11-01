package com.JK.SIMS.service.awsService;

import com.JK.SIMS.exception.CustomS3Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class S3Service {

    @Value( "${aws.s3.bucket-name}")
    private String bucketName;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Autowired
    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Uploads a file to the S3 bucket.
     *
     * @param objectKey The unique key (filename) for the object in S3.
     * @param fileBytes The file content as a byte array.
     * @param contentType The MIME type of the file (e.g. "image/png", "application/pdf").
     * @return The public URL of the uploaded object.
     */
    public String uploadFile(String objectKey, byte[] fileBytes, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) fileBytes.length)
                    // metadata for tracking
                    .metadata(Map.of(
                            "upload-timestamp", String.valueOf(System.currentTimeMillis()),
                            "content-length", String.valueOf(fileBytes.length)
                    ))
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            log.info("Successfully uploaded {} ({} bytes) to S3 bucket {}",
                    objectKey, fileBytes.length, bucketName);

            // Return the S3 key for storage
            return objectKey;
        }catch (S3Exception e) {
            log.error("S3 error while uploading file: {}", e.getMessage());
            throw new CustomS3Exception("Failed to upload file to S3", e);
        } catch (AwsServiceException e) {
            log.error("AWS error while uploading file: {}", e.getMessage());
            throw new CustomS3Exception("Failed to upload file to S3", e);
        } catch (SdkClientException e) {
            log.error("SDK error while uploading file: {}", e.getMessage());
            throw new CustomS3Exception("Failed to upload file to S3", e);
        }
    }

    /**
     * Reads (downloads) a file from the S3 bucket.
     *
     * @param objectKey The key (filename) of the object to download.
     * @return The file content as a byte array.
     * @throws IOException if the file cannot be read.
     */
    public byte[] readFile(String objectKey) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        // The try-with-resources statement ensures the stream is automatically closed
        try (ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest)) {
            log.info("Successfully read {} from S3 bucket {}", objectKey, bucketName);
            return s3ObjectStream.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read file {} from S3: {}", objectKey, e.getMessage());
            throw new CustomS3Exception("Failed to read file from S3", e);
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: {}", objectKey);
            throw new CustomS3Exception("File not found: " + objectKey);
        } catch (S3Exception e) {
            log.error("S3 error while downloading file: {}", e.getMessage());
            throw new CustomS3Exception("Failed to download file from S3", e);
        }
    }

    /**
     * Deletes a file from the S3 bucket.
     * Used for rollback when transaction fails.
     *
     * @param objectKey The key (filename) of the object to delete.
     */
    public void deleteFile(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Successfully deleted {} from S3 bucket {}", objectKey, bucketName);

        } catch (S3Exception e) {
            log.error("S3 error while deleting file {}: {}", objectKey, e.getMessage());
            throw new CustomS3Exception("Failed to delete file from S3", e);
        }
    }

    /**
     * Generate a pre-signed URL for temporary access to a private S3 object.
     * This URL will work even though the bucket is private.
     *
     * @param objectKey The S3 object key
     * @param duration How long the URL should be valid (e.g. Duration.ofHours(24))
     * @return A temporary URL that grants access to the file
     */
    public String generatePresignedUrl(String objectKey, Duration duration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            String url = presignedRequest.url().toString();
            log.debug("Generated presigned URL for {} valid for {} seconds", objectKey, duration.getSeconds());
            return url;
        } catch (S3Exception e) {
            log.error("Error generating presigned URL for {}: {}", objectKey, e.getMessage());
            throw new CustomS3Exception("Failed to generate presigned URL", e);
        }
    }
}

