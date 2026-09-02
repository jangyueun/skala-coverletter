package com.team.careerfit.global.security;

import com.team.careerfit.global.exception.AuthException;
import com.team.careerfit.user.entity.User;
import com.team.careerfit.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 요청을 보낸 사용자를 꺼낸다.
 *
 * <p>세션에는 ID 만 있고 사용자 정보는 매번 DB 에서 읽는다. 세션에 사용자 객체를
 * 캐시해 두면 이름이 바뀌거나 계정이 지워져도 세션이 살아 있는 동안 옛 값을 쓴다.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<User> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object userId = session.getAttribute(SessionKeys.USER_ID);
        if (!(userId instanceof Long id)) {
            return Optional.empty();
        }
        // 세션은 살아 있는데 사용자가 지워진 경우가 있다. 그때는 로그인 안 된 것으로 본다.
        return users.findById(id);
    }

    public User require(HttpServletRequest request) {
        return find(request).orElseThrow(AuthException::loginRequired);
    }
}
