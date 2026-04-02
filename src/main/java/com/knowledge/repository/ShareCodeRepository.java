package com.knowledge.repository;

import com.knowledge.entity.ShareCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareCodeRepository extends JpaRepository<ShareCode, String> {
}
