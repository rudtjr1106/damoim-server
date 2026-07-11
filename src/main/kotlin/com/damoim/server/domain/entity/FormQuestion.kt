package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "form_questions")
class FormQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0

    @Column(name = "text", nullable = false, columnDefinition = "text")
    var text: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    var type: QuestionType = QuestionType.SELECT

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = true, columnDefinition = "jsonb")
    var options: String? = null

    @Column(name = "required", nullable = false)
    var required: Boolean = true

    @Column(name = "position", nullable = false)
    var position: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
