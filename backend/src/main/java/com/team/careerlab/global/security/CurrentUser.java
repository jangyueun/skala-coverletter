package com.team.careerlab.global.security;

import com.team.careerlab.global.exception.AuthException;
import com.team.careerlab.user.entity.User;
import com.team.careerlab.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 요청을 보낸 사용자를 꺼낸다.
 *
 * <p>세션에는 ID 만 있고 사용자 정보는 매번 DB 에서 읽는다. 세션에 사용자 객체를
 * 캐시해 두면 이름이 바뀌거나 계정이 지워져도 세션이 살아 있는 동안 옛 값을 쓴다.
 *
 * <p>대신 <b>한 요청 안에서는</b> 한 번만 읽는다. {@link SessionAuthInterceptor} 가 먼저 부르고 컨트롤러가
 * 다시 부르는데, 둘 다 DB 를 치면 API 마다 사용자 조회가 두 번이 된다. 첫 결과를 요청 속성에 두고 재사용한다 —
 * 요청이 끝나면 같이 사라지므로 세션 캐시의 문제(옛 값)는 생기지 않는다.
 */
@Component
public class CurrentUser {

    /** 요청 속성 키. 같은 요청 안의 두 번째 호출부터는 DB 대신 여기서 읽는다. */
    static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName() + ".user";

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<User> find(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof User cached) {
            return Optional.of(cached);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object userId = session.getAttribute(SessionKeys.USER_ID);
        if (!(userId instanceof Long id)) {
            return Optional.empty();
        }
        // 세션은 살아 있는데 사용자가 지워진 경우가 있다. 그때는 로그인 안 된 것으로 본다.
        Optional<User> found = users.findById(id);
        found.ifPresent(user -> request.setAttribute(REQUEST_ATTRIBUTE, user));
        return found;
    }

    public User require(HttpServletRequest request) {
        return find(request).orElseThrow(AuthException::loginRequired);
    }
}
