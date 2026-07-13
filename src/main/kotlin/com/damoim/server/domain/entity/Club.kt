package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "clubs")
class Club {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "name", nullable = false, length = 100)
    var name: String = ""

    @Column(name = "category", nullable = false, length = 50)
    var category: String = ""

    @Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String = ""

    @Column(name = "join_code", nullable = true, length = 20)
    var joinCode: String? = null

    @Column(name = "join_code_active", nullable = false)
    var joinCodeActive: Boolean = true

    @Column(name = "emblem_color", nullable = false)
    var emblemColor: Long = 4281298387

    /** 앱에서 올린 동아리 대표 이미지의 S3 키. 있으면 응답 imageUrl은 presigned view로 파생. */
    @Column(name = "image_key", nullable = true, columnDefinition = "text")
    var imageKey: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
