package com.team.careerfit.user.repository;

import com.team.careerfit.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 식별 키는 (teamId, userId) 쌍이다. 이메일로 찾지 않는다. */
    Optional<User> findBySlackTeamIdAndSlackUserId(String slackTeamId, String slackUserId);
}
