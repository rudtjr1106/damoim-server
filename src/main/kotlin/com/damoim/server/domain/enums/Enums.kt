package com.damoim.server.domain.enums

/**
 * 도메인 enum 모음. DB에는 varchar + CHECK 제약으로 저장되고,
 * JPA에서는 `@Enumerated(EnumType.STRING)`으로 매핑한다(enum name == CHECK 값).
 * 값은 V1__init.sql의 CHECK 제약과 1:1 일치해야 한다.
 */

// ── Identity ──
enum class UserStatus { ACTIVE, SUSPENDED, WITHDRAWN }
enum class OAuthProvider { KAKAO }

// ── Club / Membership ──
enum class MemberRole { LEADER, STAFF, MEMBER }     // 명부 등급
enum class MemberStatus { ACTIVE, DORMANT }
enum class JoinStatus { PENDING, APPROVED, REJECTED }

// ── Board ──
enum class BoardCategory { NOTICE, FREE, RECRUIT }
enum class AttachmentType { IMAGE, FILE_DOC, LINK }
enum class RecruitStatus { OPEN, CLOSED }
enum class ReportReason { SPAM, ABUSE, SEXUAL, FRAUD, PRIVACY, ETC }

// ── Resources ──
enum class ResourceFolder { DOCS, ACCOUNTING, PRESENTATION, PHOTOS }
enum class ResourceVisibility { ALL_MEMBERS, COHORT_ONLY }

// ── Schedule / Events ──
enum class ScheduleType { SCHEDULE, EVENT }
enum class ScheduleAccent { PRIMARY, SKY }
enum class EventStatus { OPEN, CLOSED, ENDED }
enum class QuestionType { SELECT, MULTI, TEXT }
enum class ApplicantStatus { APPLIED, CANCELED }

// ── Settings / Subscription / Admin / Notification ──
enum class PlanTier { FREE, STANDARD, PRO }
enum class SubscriptionStatus { ACTIVE, CANCELED }
enum class PermissionType { NOTICE_WRITE, JOIN_APPROVE, BOARD_MANAGE, MEMBER_MANAGE, SCHEDULE_MANAGE, CLUB_SETTINGS }
enum class NotificationType { JOIN_APPROVED, NOTICE, COMMENT, SCHEDULE, VOTE }
