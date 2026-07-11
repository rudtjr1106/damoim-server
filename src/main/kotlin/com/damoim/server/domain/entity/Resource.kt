package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "resources")
class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "folder", nullable = false, length = 20)
    var folder: ResourceFolder = ResourceFolder.DOCS

    @Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @Column(name = "file_name", nullable = false, length = 255)
    var fileName: String = ""

    @Column(name = "ext", nullable = false, length = 16)
    var ext: String = ""

    @Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String = ""

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0

    @Column(name = "uploader_id", nullable = true)
    var uploaderId: Long? = null

    @Column(name = "download_count", nullable = false)
    var downloadCount: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    var visibility: ResourceVisibility = ResourceVisibility.ALL_MEMBERS

    @Column(name = "page_count", nullable = true)
    var pageCount: Int? = null

    @Column(name = "storage_url", nullable = true, columnDefinition = "text")
    var storageUrl: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
}
