package com.learnspark.gamification.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * UserBadge 复合主键：(userId, badgeId)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserBadgeId implements Serializable {

    private String userId;
    private String badgeId;

    private static final long serialVersionUID = 1L;
}
