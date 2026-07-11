package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "post_attachments")
class PostAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = false)
    var postId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    var type: AttachmentType = AttachmentType.IMAGE

    @Column(name = "image_label", nullable = true, length = 200)
    var imageLabel: String? = null

    @Column(name = "file_name", nullable = true, length = 255)
    var fileName: String? = null

    @Column(name = "size_bytes", nullable = true)
    var sizeBytes: Long? = null

    @Column(name = "link_title", nullable = true, length = 300)
    var linkTitle: String? = null

    @Column(name = "link_domain", nullable = true, length = 255)
    var linkDomain: String? = null

    @Column(name = "position", nullable = false)
    var position: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
