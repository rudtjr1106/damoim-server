package com.damoim.server.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "poll_options")
class PollOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "poll_id", nullable = false)
    var pollId: Long = 0

    @Column(name = "label", nullable = false, length = 200)
    var label: String = ""

    @Column(name = "position", nullable = false)
    var position: Int = 0
}
