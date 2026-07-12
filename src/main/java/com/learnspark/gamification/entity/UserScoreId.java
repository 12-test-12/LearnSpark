package com.learnspark.gamification.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * UserScore 复合主键：(userId, projectId)。
 *
 * <p>JPA {@code @IdClass} 要求 Serializable 且重写 equals/hashCode。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserScoreId implements Serializable {

    private String userId;
    private String projectId;

    private static final long serialVersionUID = 1L;
}
