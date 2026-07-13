package com.damoim.server.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

/** app.storage.provider=s3 일 때만 S3 빈 생성(자격증명은 기본 체인: env/IAM 역할). */
@Configuration
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "s3")
class S3Config(private val props: StorageProperties) {
    init {
        // fail-fast: 운영에서 버킷/리전 누락 시 부팅 거부
        require(props.s3.bucket.isNotBlank()) { "app.storage.s3.bucket(S3_BUCKET)은 provider=s3에서 필수입니다." }
        require(props.s3.region.isNotBlank()) { "app.storage.s3.region(S3_REGION)은 provider=s3에서 필수입니다." }
    }

    @Bean(destroyMethod = "close")
    fun s3Presigner(): S3Presigner = S3Presigner.builder().region(Region.of(props.s3.region)).build()

    @Bean(destroyMethod = "close")
    fun s3Client(): S3Client = S3Client.builder().region(Region.of(props.s3.region)).build()
}

@Service
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "s3")
class S3StorageService(
    private val props: StorageProperties,
    private val presigner: S3Presigner,
    private val s3Client: S3Client,
) : StorageService {

    private val bucket get() = props.s3.bucket
    private val expiry get() = Duration.ofSeconds(props.presignExpirySeconds)

    override val verifiesSize = true

    /**
     * HeadObject로 실제 업로드 크기 확인. 오브젝트 없으면(404) null(업로드 미완료).
     * HeadObject는 미존재 시에도 NoSuchKeyException이 아닌 일반 S3Exception(404)을 던지므로 상태코드로 판별한다.
     * 그 외(403 권한 등)는 상위로 전파 → SdkException 핸들러가 502로 매핑.
     */
    override fun objectSizeOrNull(key: String): Long? =
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength()
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }

    override fun presignUpload(key: String, contentType: String?): PresignedUpload {
        val put = PutObjectRequest.builder().bucket(bucket).key(key)
            .apply { contentType?.let { contentType(it) } }.build()
        val url = presigner.presignPutObject(
            PutObjectPresignRequest.builder().signatureDuration(expiry).putObjectRequest(put).build(),
        ).url().toString()
        return PresignedUpload(url, key, props.presignExpirySeconds)
    }

    override fun presignDownload(key: String, downloadFileName: String): String {
        // 헤더 구조 훼손 방지: 따옴표·제어문자 제거
        val safeName = downloadFileName.replace(Regex("[\"\\r\\n\\x00-\\x1f]"), "_")
        val get = GetObjectRequest.builder().bucket(bucket).key(key)
            .responseContentDisposition("attachment; filename=\"$safeName\"").build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(expiry).getObjectRequest(get).build(),
        ).url().toString()
    }

    override fun presignView(key: String): String {
        // 인라인 표시(이미지) — attachment 디스포지션 강제 없이 GET 서명만.
        val get = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(expiry).getObjectRequest(get).build(),
        ).url().toString()
    }

    override fun delete(key: String) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    /** 프리픽스 아래 전 오브젝트를 페이지네이션으로 나열(orphan 스윕용). */
    override fun listObjects(prefix: String): List<StoredObject> {
        val out = mutableListOf<StoredObject>()
        var token: String? = null
        do {
            val req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix)
                .apply { token?.let { continuationToken(it) } }.build()
            val resp = s3Client.listObjectsV2(req)
            resp.contents().forEach { out += StoredObject(it.key(), it.lastModified().toEpochMilli()) }
            token = if (resp.isTruncated == true) resp.nextContinuationToken() else null
        } while (token != null)
        return out
    }
}
