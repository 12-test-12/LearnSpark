package com.learnspark.auth.repository;

import com.learnspark.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓库，提供按邮箱查询（登录 / 注册查重）。
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /** 按邮箱查找用户（登录校验 & 注册查重） */
    Optional<User> findByEmail(String email);

    /** 判断邮箱是否已存在（注册查重，比 findByEmail 更轻量） */
    boolean existsByEmail(String email);
}
