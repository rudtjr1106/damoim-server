package com.damoim.server.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "resource_cohorts")
class ResourceCohort {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "resource_id", nullable = false)
    var resourceId: Long = 0

    @Column(name = "cohort_id", nullable = false)
    var cohortId: Long = 0
}
