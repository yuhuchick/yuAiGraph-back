package com.knowledge.repository;

import com.knowledge.entity.PromptConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptConfigRepository extends JpaRepository<PromptConfig, String> {
}
