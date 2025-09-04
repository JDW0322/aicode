package com.dw.daicodemother.innerservice;

import com.dw.daicodemother.exception.BusinessException;
import com.dw.daicodemother.exception.ErrorCode;
import com.dw.daicodemother.model.entity.User;
import com.dw.daicodemother.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;


import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static com.dw.daicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/dw0322">dw</a>
 */
public interface InnerUserService {

    List<User> listByIds(Collection<? extends Serializable> ids);

    User getById(Serializable id);

    UserVO getUserVO(User user);

    // 静态方法，避免跨服务调用
    static User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
}


