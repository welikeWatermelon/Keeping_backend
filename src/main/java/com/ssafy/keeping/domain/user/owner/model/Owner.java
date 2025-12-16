package com.ssafy.keeping.domain.user.owner.model;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "owners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private AuthProvider providerType;

    @Column(name = "email", nullable = true, length = 250)
    private String email;

    @Column(name = "phone_number", nullable = true length = 50)
    private String phoneNumber;

    @Column(name = "birth", nullable = true)
    private LocalDate birth;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = true)
    private Gender gender;

    @Column(name = "img_url", length = 200)
    private String imgUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "points", nullable = false)
    @Builder.Default
    private Long points = 0L;

    /**
     * 포인트 추가 (선결제 시 점주에게 포인트 적립)
     */
    public void addPoints(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("적립 포인트는 0보다 커야 합니다.");
        }
        this.points += amount;
    }

    /**
     * 포인트 차감 (점주가 포인트 출금 시)
     */
    public void deductPoints(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("차감 포인트는 0보다 커야 합니다.");
        }
        if (this.points < amount) {
            throw new IllegalStateException("포인트가 부족합니다.");
        }
        this.points -= amount;
    }
}
